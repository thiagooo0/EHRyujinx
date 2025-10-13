using Ryujinx.Common.Configuration.Hid;
using System;
using System.Collections.Generic;
using System.Numerics;
using static SDL2.SDL;

namespace Ryujinx.Input.SDL2
{
    internal class SDL2JoyConPair : IGamepad
    {
        internal readonly struct Descriptor
        {
            public Descriptor(string id, int leftIndex, int rightIndex, string leftId, string rightId)
            {
                Id = id;
                LeftIndex = leftIndex;
                RightIndex = rightIndex;
                LeftId = leftId;
                RightId = rightId;
            }

            public string Id { get; }
            public int LeftIndex { get; }
            public int RightIndex { get; }
            public string LeftId { get; }
            public string RightId { get; }
        }

        private readonly IGamepad _left;
        private readonly IGamepad _right;

        public SDL2JoyConPair(string id, IGamepad left, IGamepad right)
        {
            Id = id;
            _left = left;
            _right = right;
        }

        public string Id { get; }

        public GamepadFeaturesFlag Features => (_left?.Features ?? GamepadFeaturesFlag.None) |
                                               (_right?.Features ?? GamepadFeaturesFlag.None);

        string IGamepad.Id => Id;

        public string Name => "* Nintendo Switch Joy-Con (L/R)";

        public bool IsConnected => _left is { IsConnected: true } && _right is { IsConnected: true };

        public void Dispose()
        {
            _left?.Dispose();
            _right?.Dispose();
        }

        public GamepadStateSnapshot GetMappedStateSnapshot()
        {
            return GetStateSnapshot();
        }

        public Vector3 GetMotionData(MotionInputId inputId)
        {
            return inputId switch
            {
                MotionInputId.Accelerometer or
                    MotionInputId.Gyroscope => _left.GetMotionData(inputId),
                MotionInputId.SecondAccelerometer => _right.GetMotionData(MotionInputId.Accelerometer),
                MotionInputId.SecondGyroscope => _right.GetMotionData(MotionInputId.Gyroscope),
                _ => Vector3.Zero
            };
        }

        public GamepadStateSnapshot GetStateSnapshot()
        {
            return IGamepad.GetStateSnapshot(this);
        }

        public (float, float) GetStick(StickInputId inputId)
        {
            return inputId switch
            {
                StickInputId.Left => _left.GetStick(StickInputId.Left),
                StickInputId.Right => _right.GetStick(StickInputId.Right),
                _ => (0, 0)
            };
        }

        public bool IsPressed(GamepadButtonInputId inputId)
        {
            return _left.IsPressed(inputId) || _right.IsPressed(inputId);
        }

        public void Rumble(float lowFrequency, float highFrequency, uint durationMs)
        {
            if (lowFrequency != 0)
            {
                _right.Rumble(lowFrequency, lowFrequency, durationMs);
            }

            if (highFrequency != 0)
            {
                _left.Rumble(highFrequency, highFrequency, durationMs);
            }

            if (lowFrequency == 0 && highFrequency == 0)
            {
                _left.Rumble(0, 0, durationMs);
                _right.Rumble(0, 0, durationMs);
            }
        }

        public void SetConfiguration(InputConfig configuration)
        {
            _left.SetConfiguration(configuration);
            _right.SetConfiguration(configuration);
        }

        public void SetLed(uint packedRgb)
        {
        }

        public void SetTriggerThreshold(float triggerThreshold)
        {
            _left.SetTriggerThreshold(triggerThreshold);
            _right.SetTriggerThreshold(triggerThreshold);
        }

        public static string BuildId(string leftId, string rightId)
        {
            return $"JoyConPair:{leftId}+{rightId}";
        }

        public static List<Descriptor> DetectPairs(IReadOnlyList<string> gamepadsIds)
        {
            List<int> leftIndices = new();
            List<int> rightIndices = new();

            for (int index = 0; index < gamepadsIds.Count; index++)
            {
                string name = SDL_GameControllerNameForIndex(index);

                if (name == SDL2JoyCon.LeftName)
                {
                    leftIndices.Add(index);
                }
                else if (name == SDL2JoyCon.RightName)
                {
                    rightIndices.Add(index);
                }
            }

            int pairCount = Math.Min(leftIndices.Count, rightIndices.Count);
            List<Descriptor> result = new(pairCount);

            for (int pairIndex = 0; pairIndex < pairCount; pairIndex++)
            {
                int leftIndex = leftIndices[pairIndex];
                int rightIndex = rightIndices[pairIndex];

                string leftId = gamepadsIds[leftIndex];
                string rightId = gamepadsIds[rightIndex];

                result.Add(new Descriptor(BuildId(leftId, rightId), leftIndex, rightIndex, leftId, rightId));
            }

            return result;
        }

        public static SDL2JoyConPair Create(Descriptor descriptor)
        {
            nint leftGamepadHandle = SDL_GameControllerOpen(descriptor.LeftIndex);
            nint rightGamepadHandle = SDL_GameControllerOpen(descriptor.RightIndex);

            if (leftGamepadHandle == nint.Zero || rightGamepadHandle == nint.Zero)
            {
                if (leftGamepadHandle != nint.Zero)
                {
                    SDL_GameControllerClose(leftGamepadHandle);
                }

                if (rightGamepadHandle != nint.Zero)
                {
                    SDL_GameControllerClose(rightGamepadHandle);
                }

                return null;
            }

            return new SDL2JoyConPair(descriptor.Id,
                new SDL2JoyCon(leftGamepadHandle, descriptor.LeftId),
                new SDL2JoyCon(rightGamepadHandle, descriptor.RightId));
        }
    }
}
