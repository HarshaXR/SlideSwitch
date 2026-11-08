using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Navigation;
using SlideSwitch.Controls;
using SlideSwitch.Services;

namespace SlideSwitch.Pages;

/// <summary>
/// Connect tab. No phones: the pairing card (QR ⇄ manual). With phones: the connected list on
/// the left and a single "Pair another device" button on the right, which reopens pairing.
/// </summary>
public sealed partial class ConnectPage : Page
{
    private readonly AppCore _core = AppCore.Current;
    private bool _manual;
    private bool _pairingAnother;
    private bool _showingPhones;
    private string? _renderedQrFor;
    private string? _renderedCode;
    private int _lastClientCount;

    public ConnectPage()
    {
        InitializeComponent();
        PhonesList.ItemsSource = _core.Clients;
        Motion.Prepare(PairCard);
        Motion.Prepare(ConnectedView);
        Loaded += (_, _) => StartScanSweep();
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        _core.Changed += Refresh;
        _lastClientCount = _core.Clients.Count;
        Refresh();
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        _core.Changed -= Refresh;
    }

    private void Refresh()
    {
        // A new phone (or the last one leaving) returns to the phones list.
        var count = _core.Clients.Count;
        if (count > _lastClientCount || count == 0) _pairingAnother = false;
        _lastClientCount = count;

        var showPhones = count > 0 && !_pairingAnother;
        if (showPhones != _showingPhones)
        {
            _showingPhones = showPhones;
            if (showPhones) Motion.Swap(ConnectedView, PairCard, dx: 40, dy: 0);
            else Motion.Swap(PairCard, ConnectedView, dx: -40, dy: 0);
        }
        BackButton.Visibility = count > 0 ? Visibility.Visible : Visibility.Collapsed;
        PhonesTitle.Text = count == 1 ? "Connected Phone" : "Connected Phones";

        // Status strip.
        StatusText.Text = count switch
        {
            0 => "Waiting for a phone",
            1 => $"{_core.Clients[0].Name} connected",
            _ => $"{count} phones connected",
        };
        StatusDot.Fill = (Brush)Application.Current.Resources[count > 0 ? "SystemFillColorSuccessBrush" : "SystemFillColorCautionBrush"];
        ErrorPill.Visibility = _core.ServerError is null ? Visibility.Collapsed : Visibility.Visible;
        ErrorText.Text = _core.ServerError is { } err ? $"Server error: {err}" : "";

        // QR (re-render only when the pairing URL changes).
        if (_core.PairingUrl is { } url && url != _renderedQrFor)
        {
            _renderedQrFor = url;
            _ = RenderQrAsync(url);
        }
        if (_core.Code != _renderedCode && _manual) BuildManualDetails();
    }

    private async Task RenderQrAsync(string url)
    {
        QrImage.Source = await AppCore.RenderQrAsync(url);
        Motion.Enter(QrImage, 0.85f);
    }

    private void Mode_Click(object sender, RoutedEventArgs e)
    {
        _manual = !_manual;
        if (_manual)
        {
            BuildManualDetails();
            Motion.Swap(ManualSurface, QrSurface);
            Motion.Swap(ManualHeading, QrHeading);
        }
        else
        {
            Motion.Swap(QrSurface, ManualSurface, dy: -16);
            Motion.Swap(QrHeading, ManualHeading, dy: -16);
        }
        ModeText.Text = _manual ? "Pair with QR" : "Pair Manually";
        ModeIcon.Glyph = _manual ? "" : "";
    }

    private void PairAnother_Click(object sender, RoutedEventArgs e)
    {
        _pairingAnother = true;
        Refresh();
    }

    private void Back_Click(object sender, RoutedEventArgs e)
    {
        _pairingAnother = false;
        Refresh();
    }

    /// <summary>Code tiles, address and device name; re-adding children replays the staggered entrance.</summary>
    private void BuildManualDetails()
    {
        _renderedCode = _core.Code;
        ManualDetails.Children.Clear();

        var tiles = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        foreach (var digit in _core.Code)
        {
            tiles.Children.Add(new Border
            {
                Width = 44,
                Height = 56,
                CornerRadius = new CornerRadius(12),
                Background = (Brush)Application.Current.Resources["SubtleFillColorSecondaryBrush"],
                Child = new TextBlock
                {
                    Text = digit.ToString(),
                    FontSize = 32,
                    FontWeight = FontWeights.Bold,
                    HorizontalAlignment = HorizontalAlignment.Center,
                    VerticalAlignment = VerticalAlignment.Center,
                },
            });
        }
        ManualDetails.Children.Add(Row("PAIRING CODE", tiles));

        var address = _core.IpAddress is { } ip ? $"{ip}:{_core.Port}" : "No network";
        ManualDetails.Children.Add(Row("IP ADDRESS", new TextBlock
        {
            Text = address,
            FontSize = 24,
            FontWeight = FontWeights.SemiBold,
            FontFamily = new FontFamily("Cascadia Mono, Consolas"),
            IsTextSelectionEnabled = true,
        }));
        ManualDetails.Children.Add(Row("DEVICE NAME", new TextBlock
        {
            Text = _core.ServerName,
            FontSize = 22,
            FontWeight = FontWeights.SemiBold,
            TextTrimming = TextTrimming.CharacterEllipsis,
        }));

        var newCode = new Button
        {
            CornerRadius = new CornerRadius(16),
            Content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 8,
                Children = { new FontIcon { Glyph = "", FontSize = 13 }, new TextBlock { Text = "New code" } },
            },
        };
        newCode.Click += (_, _) => _core.NewCode();
        ManualDetails.Children.Add(newCode);
    }

    private static StackPanel Row(string label, UIElement content) => new()
    {
        Spacing = 6,
        Children =
        {
            new TextBlock
            {
                Text = label,
                FontSize = 11,
                FontWeight = FontWeights.SemiBold,
                CharacterSpacing = 60,
                Foreground = (Brush)Application.Current.Resources["TextFillColorSecondaryBrush"],
            },
            content,
        },
    };

    /// <summary>Gentle scanner line sweeping over the QR (skipped when animations are turned off).</summary>
    private void StartScanSweep()
    {
        var settings = new Windows.UI.ViewManagement.UISettings();
        if (!settings.AnimationsEnabled)
        {
            ScanLine.Visibility = Visibility.Collapsed;
            return;
        }
        var animation = new DoubleAnimation
        {
            From = -28,
            To = 290 - 28,
            Duration = new Duration(TimeSpan.FromSeconds(2.4)),
            AutoReverse = true,
            RepeatBehavior = RepeatBehavior.Forever,
            EasingFunction = new SineEase { EasingMode = EasingMode.EaseInOut },
        };
        Storyboard.SetTarget(animation, ScanShift);
        Storyboard.SetTargetProperty(animation, "Y");
        var storyboard = new Storyboard();
        storyboard.Children.Add(animation);
        storyboard.Begin();
    }
}
