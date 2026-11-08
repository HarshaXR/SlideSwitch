using Makaretu.Dns;

namespace SlideSwitch.Services;

/// <summary>Advertises the server as _slideswitch._tcp over mDNS / DNS-SD.</summary>
public sealed class MdnsAdvertiser : IDisposable
{
    private MulticastService? _mdns;
    private ServiceDiscovery? _discovery;
    private ServiceProfile? _profile;

    public void Advertise(string instanceName, int port, string serverId)
    {
        Stop();
        _profile = new ServiceProfile(instanceName, "_slideswitch._tcp", (ushort)port);
        _profile.AddProperty("v", "1");
        _profile.AddProperty("platform", "windows");
        _profile.AddProperty("id", serverId);
        _mdns = new MulticastService();
        _discovery = new ServiceDiscovery(_mdns);
        _mdns.Start();
        _discovery.Advertise(_profile);
        _discovery.Announce(_profile);
    }

    public void Stop()
    {
        if (_discovery is not null && _profile is not null)
        {
            try { _discovery.Unadvertise(_profile); } catch { }
        }
        _discovery?.Dispose();
        _discovery = null;
        _mdns?.Stop();
        _mdns?.Dispose();
        _mdns = null;
        _profile = null;
    }

    public void Dispose() => Stop();
}
