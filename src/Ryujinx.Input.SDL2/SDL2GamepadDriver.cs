using Ryujinx.Common.Logging;
using Ryujinx.SDL2.Common;
using System;
using System.Collections.Generic;
using System.Threading;
using static SDL2.SDL;

namespace Ryujinx.Input.SDL2
{
    public class SDL2GamepadDriver : IGamepadDriver
    {
        private readonly Dictionary<int, string> _gamepadsInstanceIdsMapping;
        private readonly List<string> _physicalGamepadIds;
        private readonly Dictionary<string, SDL2JoyConPair.Descriptor> _joyConPairs;
        private readonly List<string> _joyConPairIds;
        private readonly Lock _lock = new();

        public ReadOnlySpan<string> GamepadsIds
        {
            get
            {
                lock (_lock)
                {
                    string[] result = new string[_physicalGamepadIds.Count + _joyConPairIds.Count];

                    _physicalGamepadIds.CopyTo(result, 0);
                    _joyConPairIds.CopyTo(result, _physicalGamepadIds.Count);

                    return result;
                }
            }
        }

        public string DriverName => "SDL2";

        public event Action<string> OnGamepadConnected;
        public event Action<string> OnGamepadDisconnected;

        public SDL2GamepadDriver()
        {
            _gamepadsInstanceIdsMapping = new Dictionary<int, string>();
            _physicalGamepadIds = [];
            _joyConPairs = new Dictionary<string, SDL2JoyConPair.Descriptor>();
            _joyConPairIds = [];

            SDL2Driver.Instance.Initialize();
            SDL2Driver.Instance.OnJoyStickConnected += HandleJoyStickConnected;
            SDL2Driver.Instance.OnJoystickDisconnected += HandleJoyStickDisconnected;
            SDL2Driver.Instance.OnJoyBatteryUpdated += HandleJoyBatteryUpdated;

            // Add already connected gamepads
            int numJoysticks = SDL_NumJoysticks();

            for (int joystickIndex = 0; joystickIndex < numJoysticks; joystickIndex++)
            {
                HandleJoyStickConnected(joystickIndex, SDL_JoystickGetDeviceInstanceID(joystickIndex));
            }
        }

        private string GenerateGamepadId(int joystickIndex)
        {
            Guid guid = SDL_JoystickGetDeviceGUID(joystickIndex);

            // Add a unique identifier to the start of the GUID in case of duplicates.

            if (guid == Guid.Empty)
            {
                return null;
            }

            // Remove the first 4 char of the guid (CRC part) to make it stable
            string guidString = "0000" + guid.ToString().Substring(4);

            string id;

            lock (_lock)
            {
                int guidIndex = 0;
                id = guidIndex + "-" + guidString;

                while (_physicalGamepadIds.Contains(id))
                {
                    id = (++guidIndex) + "-" + guidString;
                }
            }

            return id;
        }

        private int GetJoystickIndexByGamepadId(string id)
        {
            lock (_lock)
            {
                return _physicalGamepadIds.IndexOf(id);
            }
        }

        private void HandleJoyStickDisconnected(int joystickInstanceId)
        {
            string id = null;
            List<string> addedPairs = null;
            List<string> removedPairs = null;

            if (_gamepadsInstanceIdsMapping.TryGetValue(joystickInstanceId, out string existingId))
            {
                _gamepadsInstanceIdsMapping.Remove(joystickInstanceId);
                id = existingId;

                lock (_lock)
                {
                    _physicalGamepadIds.Remove(id);
                    UpdateJoyConPairsLocked(out addedPairs, out removedPairs);
                }
            }

            if (id != null)
            {
                OnGamepadDisconnected?.Invoke(id);
            }

            if (removedPairs != null)
            {
                foreach (string pairId in removedPairs)
                {
                    OnGamepadDisconnected?.Invoke(pairId);
                }
            }

            if (addedPairs != null)
            {
                foreach (string pairId in addedPairs)
                {
                    OnGamepadConnected?.Invoke(pairId);
                }
            }
        }

        private void HandleJoyStickConnected(int joystickDeviceId, int joystickInstanceId)
        {
            if (SDL_IsGameController(joystickDeviceId) == SDL_bool.SDL_TRUE)
            {
                if (_gamepadsInstanceIdsMapping.ContainsKey(joystickInstanceId))
                {
                    // Sometimes a JoyStick connected event fires after the app starts even though it was connected before
                    // so it is rejected to avoid doubling the entries.
                    return;
                }

                string id = GenerateGamepadId(joystickDeviceId);

                if (id == null)
                {
                    return;
                }

                if (_gamepadsInstanceIdsMapping.TryAdd(joystickInstanceId, id))
                {
                    List<string> addedPairs;
                    List<string> removedPairs;

                    lock (_lock)
                    {
                        if (joystickDeviceId <= _physicalGamepadIds.FindLastIndex(_ => true))
                        {
                            _physicalGamepadIds.Insert(joystickDeviceId, id);
                        }
                        else
                        {
                            _physicalGamepadIds.Add(id);
                        }

                        UpdateJoyConPairsLocked(out addedPairs, out removedPairs);
                    }

                    OnGamepadConnected?.Invoke(id);

                    foreach (string pairId in removedPairs)
                    {
                        OnGamepadDisconnected?.Invoke(pairId);
                    }

                    foreach (string pairId in addedPairs)
                    {
                        OnGamepadConnected?.Invoke(pairId);
                    }
                }
            }
        }
        
        private void HandleJoyBatteryUpdated(int joystickDeviceId, SDL_JoystickPowerLevel powerLevel)
        {
            Logger.Info?.Print(LogClass.Hid,
                $"{SDL_GameControllerNameForIndex(joystickDeviceId)} power level: {powerLevel}");
        }


        protected virtual void Dispose(bool disposing)
        {
            if (disposing)
            {
                SDL2Driver.Instance.OnJoyStickConnected -= HandleJoyStickConnected;
                SDL2Driver.Instance.OnJoystickDisconnected -= HandleJoyStickDisconnected;

                // Simulate a full disconnect when disposing
                foreach (string id in GamepadsIds)
                {
                    OnGamepadDisconnected?.Invoke(id);
                }

                lock (_lock)
                {
                    _physicalGamepadIds.Clear();
                    _joyConPairs.Clear();
                    _joyConPairIds.Clear();
                }

                SDL2Driver.Instance.Dispose();
            }
        }

        public void Dispose()
        {
            GC.SuppressFinalize(this);
            Dispose(true);
        }

        public IGamepad GetGamepad(string id)
        {
            SDL2JoyConPair.Descriptor descriptor;
            int joystickIndex = -1;

            lock (_lock)
            {
                if (_joyConPairs.TryGetValue(id, out descriptor))
                {
                    // Use descriptor outside lock.
                }
                else
                {
                    joystickIndex = _physicalGamepadIds.IndexOf(id);
                    if (joystickIndex == -1)
                    {
                        return null;
                    }
                }
            }

            if (joystickIndex != -1)
            {
                IntPtr gamepadHandle = SDL_GameControllerOpen(joystickIndex);

                if (gamepadHandle == IntPtr.Zero)
                {
                    return null;
                }

                if (SDL_GameControllerName(gamepadHandle).StartsWith(SDL2JoyCon.Prefix))
                {
                    return new SDL2JoyCon(gamepadHandle, id);
                }

                return new SDL2Gamepad(gamepadHandle, id);
            }

            SDL2JoyConPair pair = SDL2JoyConPair.Create(descriptor);

            return pair;
        }

        private void UpdateJoyConPairsLocked(out List<string> addedPairs, out List<string> removedPairs)
        {
            List<SDL2JoyConPair.Descriptor> descriptors = SDL2JoyConPair.DetectPairs(_physicalGamepadIds);

            HashSet<string> newIds = new();
            List<string> orderedIds = new(descriptors.Count);

            addedPairs = new List<string>();
            removedPairs = new List<string>();

            foreach (SDL2JoyConPair.Descriptor descriptor in descriptors)
            {
                newIds.Add(descriptor.Id);
                orderedIds.Add(descriptor.Id);

                if (!_joyConPairs.ContainsKey(descriptor.Id))
                {
                    addedPairs.Add(descriptor.Id);
                }

                _joyConPairs[descriptor.Id] = descriptor;
            }

            foreach (string existingId in _joyConPairIds)
            {
                if (!newIds.Contains(existingId))
                {
                    removedPairs.Add(existingId);
                    _joyConPairs.Remove(existingId);
                }
            }

            _joyConPairIds.Clear();
            _joyConPairIds.AddRange(orderedIds);
        }
    }
}
