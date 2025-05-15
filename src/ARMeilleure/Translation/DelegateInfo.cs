using System;

namespace ARMeilleure.Translation
{
    public class DelegateInfo
    {
        public IntPtr FuncPtr { get; }

        public DelegateInfo(IntPtr funcPtr)
        {
            FuncPtr = funcPtr;
        }
    }
}
