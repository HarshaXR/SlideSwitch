using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media.Animation;
using SlideSwitch.Controls;
using SlideSwitch.Pages;
using SlideSwitch.Services;

namespace SlideSwitch;

public sealed partial class MainWindow : Window
{
    private readonly AppCore _core;

    public MainWindow()
    {
        InitializeComponent();
        _core = new AppCore(Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread());

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);
        AppWindow.Resize(new Windows.Graphics.SizeInt32(1180, 820));
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        AppWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;

        ApplyAppearance(_core.Settings.Appearance);

        Nav.ItemWidth = 150;
        Nav.SetItems(new[] { ("Connect", ""), ("Settings", "") });
        Nav.SelectionChanged += index =>
        {
            var page = index == 0 ? typeof(ConnectPage) : typeof(SettingsPage);
            var effect = index == 0 ? SlideNavigationTransitionEffect.FromLeft : SlideNavigationTransitionEffect.FromRight;
            PageFrame.Navigate(page, null, new SlideNavigationTransitionInfo { Effect = effect });
        };
        PageFrame.Navigate(typeof(ConnectPage), null, new SuppressNavigationTransitionInfo());

        if (!_core.Settings.Onboarded)
        {
            Welcome.Visibility = Visibility.Visible;
            Motion.Prepare(Welcome);
            Root.Loaded += (_, _) => Motion.Enter(WelcomeCard, 0.92f);
        }

        _core.Start();
        Closed += (_, _) => _core.Dispose();
    }

    /// <summary>"System", "Light" or "Dark": theme for all content plus readable caption buttons.</summary>
    public void ApplyAppearance(string appearance)
    {
        Root.RequestedTheme = appearance switch
        {
            "Light" => ElementTheme.Light,
            "Dark" => ElementTheme.Dark,
            _ => ElementTheme.Default,
        };
        var dark = appearance == "Dark" ||
                   (appearance != "Light" && Application.Current.RequestedTheme == ApplicationTheme.Dark);
        AppWindow.TitleBar.ButtonForegroundColor = dark ? Colors.White : Colors.Black;
        AppWindow.TitleBar.ButtonHoverBackgroundColor = dark ? ColorHelper.FromArgb(0x20, 0xFF, 0xFF, 0xFF) : ColorHelper.FromArgb(0x14, 0, 0, 0);
    }

    private void Continue_Click(object sender, RoutedEventArgs e)
    {
        _core.Settings.Onboarded = true;
        _core.SaveSettings();
        Welcome.Opacity = 0; // OpacityTransition animates the fade-out
        var timer = DispatcherQueue.CreateTimer();
        timer.Interval = TimeSpan.FromMilliseconds(300);
        timer.IsRepeating = false;
        timer.Tick += (_, _) => Welcome.Visibility = Visibility.Collapsed;
        timer.Start();
    }
}
