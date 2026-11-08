using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using SlideSwitch.Services;

namespace SlideSwitch.Pages;

public sealed partial class SettingsPage : Page
{
    private static readonly string[] Appearances = ["System", "Light", "Dark"];
    private readonly AppCore _core = AppCore.Current;

    public SettingsPage()
    {
        InitializeComponent();
        PhonesList.ItemsSource = _core.Clients;
        ActivityList.ItemsSource = _core.Log;

        AppearanceSelector.ItemWidth = 120;
        AppearanceSelector.SetItems(
            new[] { ("System", ""), ("Light", ""), ("Dark", "") },
            Math.Max(0, Array.IndexOf(Appearances, _core.Settings.Appearance)));
        AppearanceSelector.SelectionChanged += index =>
        {
            _core.Settings.Appearance = Appearances[index];
            _core.SaveSettings();
            App.Window?.ApplyAppearance(Appearances[index]);
        };
    }

    protected override void OnNavigatedTo(NavigationEventArgs e)
    {
        _core.Changed += Refresh;
        Refresh();
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        _core.Changed -= Refresh;
    }

    private void Refresh()
    {
        PairedText.Text = _core.PairedCount == 1 ? "1 paired phone" : $"{_core.PairedCount} paired phones";
        ForgetButton.IsEnabled = _core.PairedCount > 0;
        NoPhonesText.Visibility = _core.Clients.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        NoActivityText.Visibility = _core.Log.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        CodeInfo.Text = $"Current code {_core.FormattedCode} · port {(_core.Port > 0 ? _core.Port.ToString() : "—")}";
    }

    private void NewCode_Click(object sender, RoutedEventArgs e) => _core.NewCode();
    private void Restart_Click(object sender, RoutedEventArgs e) => _core.Start();
    private void Forget_Click(object sender, RoutedEventArgs e) => _core.ForgetAll();
}
