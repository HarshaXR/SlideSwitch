using System.Diagnostics;
using System.Runtime.InteropServices;

namespace SlideSwitch.Services;

/// <summary>Turns protocol actions into keystrokes for the foreground app via SendInput.</summary>
public static class KeyInjector
{
    private const ushort VK_RETURN = 0x0D, VK_CONTROL = 0x11, VK_ESCAPE = 0x1B,
        VK_LEFT = 0x25, VK_RIGHT = 0x27, VK_B = 0x42, VK_F5 = 0x74;

    private static readonly string[] Browsers = ["chrome", "msedge", "firefox", "brave", "opera", "vivaldi", "arc"];

    /// <summary>Keys (modifiers first) for an action, or null if the action is unknown.</summary>
    public static ushort[]? KeysFor(string action) => action switch
    {
        "next" => [VK_RIGHT],
        "previous" => [VK_LEFT],
        "end" => [VK_ESCAPE],
        "black" => [VK_B],
        // PowerPoint & most apps: F5 (from start). Google Slides in a browser: Ctrl+F5.
        "start" => IsBrowserInFront() ? new[] { VK_CONTROL, VK_F5 } : new[] { VK_F5 },
        _ => null,
    };

    /// <summary>Presses the keys as a chord. Returns false if Windows rejected the input
    /// (e.g. UIPI: the foreground app runs elevated and we don't).</summary>
    public static bool Send(ushort[] keys)
    {
        var inputs = new INPUT[keys.Length * 2];
        for (var i = 0; i < keys.Length; i++)
        {
            inputs[i] = KeyInput(keys[i], up: false);
            inputs[keys.Length * 2 - 1 - i] = KeyInput(keys[i], up: true);
        }
        var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
        return sent == inputs.Length;
    }

    public static string? ForegroundAppName()
    {
        try
        {
            var hwnd = GetForegroundWindow();
            if (hwnd == IntPtr.Zero) return null;
            GetWindowThreadProcessId(hwnd, out var pid);
            using var p = Process.GetProcessById((int)pid);
            return p.MainModule?.FileVersionInfo.FileDescription is { Length: > 0 } desc ? desc : p.ProcessName;
        }
        catch { return null; }
    }

    private static bool IsBrowserInFront()
    {
        try
        {
            GetWindowThreadProcessId(GetForegroundWindow(), out var pid);
            using var p = Process.GetProcessById((int)pid);
            return Browsers.Contains(p.ProcessName.ToLowerInvariant());
        }
        catch { return false; }
    }

    private static INPUT KeyInput(ushort vk, bool up)
    {
        uint flags = up ? KEYEVENTF_KEYUP : 0;
        if (vk is VK_LEFT or VK_RIGHT) flags |= KEYEVENTF_EXTENDEDKEY;
        return new INPUT
        {
            type = INPUT_KEYBOARD,
            U = new InputUnion { ki = new KEYBDINPUT { wVk = vk, wScan = (ushort)MapVirtualKey(vk, 0), dwFlags = flags } },
        };
    }

    // ---- Win32 interop ----
    private const uint INPUT_KEYBOARD = 1, KEYEVENTF_EXTENDEDKEY = 0x0001, KEYEVENTF_KEYUP = 0x0002;

    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT { public uint type; public InputUnion U; }

    // The union must be as large as its biggest member (MOUSEINPUT) or SendInput rejects cbSize.
    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion
    {
        [FieldOffset(0)] public MOUSEINPUT mi;
        [FieldOffset(0)] public KEYBDINPUT ki;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr dwExtraInfo; }

    [StructLayout(LayoutKind.Sequential)]
    private struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr dwExtraInfo; }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    private static extern uint MapVirtualKey(uint uCode, uint uMapType);

    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);
}
