using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;

namespace SlideSwitch.Controls;

public sealed partial class CapsuleSelector : UserControl
{
    private readonly List<(Button Button, TextBlock Label)> _items = new();

    public CapsuleSelector()
    {
        InitializeComponent();
    }

    /// <summary>Width of each segment in DIPs.</summary>
    public double ItemWidth { get; set; } = 140;

    /// <summary>Height of the whole control in DIPs.</summary>
    public double ItemHeight { get; set; } = 40;

    public int SelectedIndex { get; private set; }

    public event Action<int>? SelectionChanged;

    /// <summary>Builds the segments: (title, Segoe Fluent Icons glyph).</summary>
    public void SetItems(IEnumerable<(string Title, string Glyph)> items, int selectedIndex = 0)
    {
        ItemsPanel.Children.Clear();
        _items.Clear();
        var index = 0;
        foreach (var (title, glyph) in items)
        {
            var i = index++;
            var label = new TextBlock { Text = title, VerticalAlignment = VerticalAlignment.Center };
            var content = new StackPanel
            {
                Orientation = Orientation.Horizontal,
                Spacing = 8,
                HorizontalAlignment = HorizontalAlignment.Center,
                Children = { new FontIcon { Glyph = glyph, FontSize = 15 }, label },
            };
            var button = new Button
            {
                Content = content,
                Width = ItemWidth,
                Height = ItemHeight,
                Padding = new Thickness(0),
                CornerRadius = new CornerRadius(20),
                Background = new SolidColorBrush(Microsoft.UI.Colors.Transparent),
                BorderThickness = new Thickness(0),
                HorizontalContentAlignment = HorizontalAlignment.Center,
            };
            button.Click += (_, _) => Select(i, animate: true, raise: true);
            ItemsPanel.Children.Add(button);
            _items.Add((button, label));
        }
        Pill.Width = ItemWidth;
        Pill.Height = ItemHeight;
        Select(selectedIndex, animate: false, raise: false);
    }

    public void Select(int index, bool animate, bool raise)
    {
        if (index < 0 || index >= _items.Count) return;
        var changed = index != SelectedIndex;
        SelectedIndex = index;
        for (var i = 0; i < _items.Count; i++)
            _items[i].Label.FontWeight = i == index ? FontWeights.SemiBold : FontWeights.Normal;

        var target = index * ItemWidth;
        if (!animate)
        {
            PillShift.X = target;
        }
        else
        {
            var animation = new DoubleAnimation
            {
                To = target,
                Duration = new Duration(TimeSpan.FromMilliseconds(380)),
                EasingFunction = new BackEase { Amplitude = 0.25, EasingMode = EasingMode.EaseOut },
            };
            Storyboard.SetTarget(animation, PillShift);
            Storyboard.SetTargetProperty(animation, "X");
            var storyboard = new Storyboard();
            storyboard.Children.Add(animation);
            storyboard.Begin();
        }
        if (raise && changed) SelectionChanged?.Invoke(index);
    }
}
