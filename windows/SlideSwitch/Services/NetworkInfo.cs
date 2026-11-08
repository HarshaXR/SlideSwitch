using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace SlideSwitch.Services;

public static class NetworkInfo
{
    /// <summary>Best-guess LAN IPv4: an up, non-virtual Wi-Fi/Ethernet adapter with a gateway.</summary>
    public static string? PrimaryIPv4()
    {
        var candidates = NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up &&
                        n.NetworkInterfaceType is NetworkInterfaceType.Wireless80211 or NetworkInterfaceType.Ethernet &&
                        !n.Description.Contains("Virtual", StringComparison.OrdinalIgnoreCase) &&
                        !n.Description.Contains("Hyper-V", StringComparison.OrdinalIgnoreCase))
            .Select(n => (Props: n.GetIPProperties(), Wifi: n.NetworkInterfaceType == NetworkInterfaceType.Wireless80211))
            .Where(x => x.Props.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork))
            .OrderByDescending(x => x.Wifi);

        foreach (var (props, _) in candidates)
        {
            var addr = props.UnicastAddresses.FirstOrDefault(a => a.Address.AddressFamily == AddressFamily.InterNetwork);
            if (addr is not null) return addr.Address.ToString();
        }
        return null;
    }
}
