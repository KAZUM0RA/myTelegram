package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

public class LauncherIconController {
    public static void tryFixLauncherIconIfNeeded() {
        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(LauncherIcon.DEFAULT);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.DEFAULT;
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        PackageManager pm = ctx.getPackageManager();
        for (LauncherIcon i : LauncherIcon.values()) {
            pm.setComponentEnabledSetting(i.getComponentName(ctx), i == icon ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        }
    }

    public enum LauncherIcon {
        // Форк: типова іконка показує НАШЕ зображення, а не icon_*_sa.
        // Ті ресурси — окремі копії для попереднього перегляду, і в них
        // лишався літачок Telegram: на робочому столі вже стояла зірка, а в
        // списку вибору — старий малюнок.
        DEFAULT("DefaultIcon", R.drawable.fork_icon_background, R.mipmap.fork_cosmos_fg, R.string.AppIconDefault),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium, true),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo, true),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox, true),

        // Форк: кольорові варіанти нашої іконки. Мають бути саме в цьому
        // списку: tryFixLauncherIconIfNeeded() перевіряє лише його і скинув
        // би вибір на типову, якби вважав, що жодна іконка не ввімкнена.
        //
        // Тло чорне, як у самому зображенні; передній план — саме зображення.
        // Такий самий поділ, як в адаптивній іконці, тож попередній перегляд
        // у налаштуваннях виглядає так само, як на робочому столі.
        FORK_AMETHYST("ForkAmethystIcon", R.drawable.fork_icon_background,
                R.mipmap.fork_amethyst_fg, R.string.AppIconForkAmethyst),
        FORK_EMERALD("ForkEmeraldIcon", R.drawable.fork_icon_background,
                R.mipmap.fork_emerald_fg, R.string.AppIconForkEmerald),
        FORK_AMBER("ForkAmberIcon", R.drawable.fork_icon_background,
                R.mipmap.fork_amber_fg, R.string.AppIconForkAmber);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }
    }
}
