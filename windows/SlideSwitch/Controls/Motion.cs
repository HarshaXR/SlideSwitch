using System.Numerics;
using Microsoft.UI.Xaml;

namespace SlideSwitch.Controls;

/// <summary>
/// Small Fluent-motion helpers built on WinUI implicit transitions (OpacityTransition /
/// TranslationTransition / ScaleTransition), so swaps animate from their current values.
/// </summary>
public static class Motion
{
    private static readonly TimeSpan Fast = TimeSpan.FromMilliseconds(280);

    public static void Prepare(UIElement element)
    {
        element.OpacityTransition ??= new ScalarTransition { Duration = Fast };
        element.TranslationTransition ??= new Vector3Transition { Duration = Fast };
        element.ScaleTransition ??= new Vector3Transition { Duration = Fast };
    }

    /// <summary>Cross-swaps two elements (initially hidden ones should start Collapsed with Opacity 0): <paramref name="show"/> slides in, <paramref name="hide"/> slides out.</summary>
    public static void Swap(UIElement show, UIElement hide, float dx = 0, float dy = 16)
    {
        Prepare(show);
        Prepare(hide);

        hide.Opacity = 0;
        hide.Translation = new Vector3(-dx, -dy, 0);
        hide.IsHitTestVisible = false;

        show.Visibility = Visibility.Visible;
        show.IsHitTestVisible = true;
        show.Translation = new Vector3(dx, dy, 0);
        show.DispatcherQueue.TryEnqueue(() =>
        {
            show.Opacity = 1;
            show.Translation = Vector3.Zero;
        });

        var timer = hide.DispatcherQueue.CreateTimer();
        timer.Interval = Fast;
        timer.IsRepeating = false;
        timer.Tick += (_, _) =>
        {
            if (hide.Opacity == 0) hide.Visibility = Visibility.Collapsed;
        };
        timer.Start();
    }

    /// <summary>Gentle scale-and-fade entrance for a single element.</summary>
    public static void Enter(UIElement element, float fromScale = 0.94f)
    {
        Prepare(element);
        if (element is FrameworkElement fe)
            element.CenterPoint = new Vector3((float)fe.ActualWidth / 2, (float)fe.ActualHeight / 2, 0);
        element.Opacity = 0;
        element.Scale = new Vector3(fromScale, fromScale, 1);
        element.DispatcherQueue.TryEnqueue(() =>
        {
            element.Opacity = 1;
            element.Scale = Vector3.One;
        });
    }
}
