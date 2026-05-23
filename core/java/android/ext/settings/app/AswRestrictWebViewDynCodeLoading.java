package android.ext.settings.app;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.GosPackageState;
import android.content.pm.GosPackageStateFlag;
import android.ext.settings.ExtSettings;
import android.util.ArraySet;

import com.android.internal.R;

/** @hide */
public class AswRestrictWebViewDynCodeLoading extends AppSwitch {
    public static final AswRestrictWebViewDynCodeLoading I = new AswRestrictWebViewDynCodeLoading();

    private AswRestrictWebViewDynCodeLoading() {
        gosPsFlagNonDefault = GosPackageStateFlag.RESTRICT_WEBVIEW_DYN_CODE_LOADING_NON_DEFAULT;
        gosPsFlag = GosPackageStateFlag.RESTRICT_WEBVIEW_DYN_CODE_LOADING;
    }

    private static volatile ArraySet<String> allowedSystemPkgs;

    private static boolean shouldAllowByDefaultToSystemPackage(Context ctx, String pkg) {
        ArraySet<String> set = allowedSystemPkgs;
        if (set == null) {
            set = new ArraySet<>(ctx.getResources()
                .getStringArray(R.array.system_pkgs_allowed_webview_dyn_code_loading_by_default));
            allowedSystemPkgs = set;
        }
        return set.contains(pkg);
    }

    @Override
    public Boolean getImmutableValue(Context ctx, int userId, ApplicationInfo appInfo,
                                     GosPackageState ps, StateInfo si) {
        // allow dcl restriction toggle for all apps, including system apps.
        // default value logic still applies (system apps default to restricted
        // unless they are in the allowlist).
        if (appInfo.isSystemApp()) {
            return null;
        }

        return null;
    }

    @Override
    protected boolean getDefaultValueInner(Context ctx, int userId, ApplicationInfo appInfo,
                                           GosPackageState ps, StateInfo si) {
        if (appInfo.isSystemApp()) {
            return !shouldAllowByDefaultToSystemPackage(ctx, appInfo.packageName);
        } else {
            return ExtSettings.RESTRICT_WEBVIEW_DYN_CODE_LOADING_BY_DEFAULT.get(ctx, userId);
        }
    }
}
