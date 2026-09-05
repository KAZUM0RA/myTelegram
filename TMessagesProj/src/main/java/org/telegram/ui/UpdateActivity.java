/*
 * Форк MyTelegram — неофіційна збірка. GNU GPL v2 або пізніша.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.core.content.FileProvider;

import org.telegram.ai.AiKeyStorage;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.update.UpdateChecker;

import java.io.File;

/**
 * Екран «Оновлення»: звіряння з релізом на GitHub і встановлення.
 *
 * <p>Оновлюємося не через магазин, а прямо з релізу — збірка неофіційна й у
 * магазинах її немає. Тому потрібен дозвіл «встановлення з невідомих
 * джерел»: якщо його немає, ведемо користувача до системних налаштувань,
 * а не мовчимо.
 */
public class UpdateActivity extends BaseFragment {

    private TextSettingsCell currentCell;
    private TextSettingsCell checkCell;
    private TextSettingsCell tokenCell;
    private org.telegram.ui.Cells.TextCheckCell autoCell;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.UpdateTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        root.addView(header(context, getString(R.string.UpdateSectionBuild)));

        currentCell = row(context, null);
        currentCell.setEnabled(false);
        root.addView(currentCell);

        checkCell = row(context, v -> check());
        root.addView(checkCell);

        autoCell = new TextCheckCell(context);
        autoCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        autoCell.setTextAndCheck(getString(R.string.UpdateAutoRow),
                UpdateChecker.isAutoCheckEnabled(), false);
        autoCell.setOnClickListener(v -> {
            final boolean enabled = !UpdateChecker.isAutoCheckEnabled();
            UpdateChecker.setAutoCheckEnabled(enabled);
            autoCell.setChecked(enabled);
        });
        root.addView(autoCell);

        root.addView(info(context, getString(R.string.UpdateInfo)));

        root.addView(header(context, getString(R.string.UpdateSectionAccess)));

        tokenCell = row(context, v -> showTokenDialog());
        root.addView(tokenCell);

        final TextSettingsCell openRepo = row(context, v ->
                Browser.openUrl(getParentActivity(),
                        "https://github.com/" + UpdateChecker.REPO + "/releases"));
        openRepo.setText(getString(R.string.UpdateOpenReleases), false);
        root.addView(openRepo);

        root.addView(info(context, getString(R.string.UpdateTokenInfo)));

        updateRows();

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        scrollView.addView(root, LayoutHelper.createScroll(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        fragmentView = scrollView;
        return fragmentView;
    }

    // ── Перевірка й встановлення ─────────────────────────────────────────

    private void check() {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress =
                new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(true);
        progress.show();

        UpdateChecker.check(new UpdateChecker.CheckCallback() {
            @Override
            public void onResult(UpdateChecker.Update update) {
                progress.dismiss();
                confirmDownload(update);
            }

            @Override
            public void onUpToDate() {
                progress.dismiss();
                BulletinFactory.of(UpdateActivity.this)
                        .createSimpleBulletin(R.raw.chats_infotip,
                                getString(R.string.UpdateUpToDate))
                        .show();
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(UpdateActivity.this).createErrorBulletin(message).show();
            }
        });
    }

    private void confirmDownload(UpdateChecker.Update update) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.UpdateFoundTitle));
        builder.setMessage(LocaleController.formatString(R.string.UpdateFoundText,
                update.commit, AndroidUtilities.formatFileSize(update.size)));
        builder.setPositiveButton(getString(R.string.UpdateDownload),
                (dialog, which) -> startDownload(update));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }

    private void startDownload(UpdateChecker.Update update) {
        if (getParentActivity() == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(),
                AlertDialog.ALERT_TYPE_LOADING);
        progress.setCanCancel(false);
        progress.setTitle(getString(R.string.UpdateDownloading));
        progress.show();

        UpdateChecker.download(update, new UpdateChecker.DownloadCallback() {
            @Override
            public void onProgress(int percent) {
                progress.setProgress(percent);
            }

            @Override
            public void onReady(File apk) {
                progress.dismiss();
                install(apk);
            }

            @Override
            public void onError(String message) {
                progress.dismiss();
                BulletinFactory.of(UpdateActivity.this).createErrorBulletin(message).show();
            }
        });
    }

    private void install(File apk) {
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        // Без цього дозволу система мовчки нічого не зробить, і виглядатиме
        // це як «кнопка не працює». Тому ведемо просто до потрібного екрана.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(getString(R.string.UpdateNeedPermissionTitle));
            builder.setMessage(getString(R.string.UpdateNeedPermissionText));
            builder.setPositiveButton(getString(R.string.Settings), (dialog, which) -> {
                try {
                    context.startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.getPackageName())));
                } catch (Throwable e) {
                    FileLog.e("UpdateActivity: не вдалося відкрити налаштування");
                }
            });
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.show();
            return;
        }

        try {
            final Uri uri = FileProvider.getUriForFile(context,
                    ApplicationLoader.getApplicationId() + ".provider", apk);
            final Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable e) {
            FileLog.e("UpdateActivity: не вдалося запустити встановлення");
            BulletinFactory.of(this)
                    .createErrorBulletin(getString(R.string.UpdateErrorInstall)).show();
        }
    }

    // ── Токен ────────────────────────────────────────────────────────────

    private void showTokenDialog() {
        final Context context = getParentActivity();
        if (context == null || !AiKeyStorage.isAvailable()) {
            return;
        }

        final EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setBackgroundDrawable(null);
        editText.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setHint("github_pat_…");

        final FrameLayout container = new FrameLayout(context);
        container.addView(editText, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP, 24, 6, 24, 0));

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.UpdateTokenRow));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Save), (dialog, which) -> {
            final String entered = editText.getText().toString().trim();
            if (entered.isEmpty()) {
                return;
            }
            final boolean saved = AiKeyStorage.saveKey(UpdateChecker.TOKEN_PROVIDER, entered);
            updateRows();
            BulletinFactory.of(this)
                    .createSimpleBulletin(saved ? R.raw.chats_infotip : R.raw.error,
                            getString(saved ? R.string.AiKeySaved : R.string.AiKeySaveFailed))
                    .show();
        });
        if (AiKeyStorage.hasKey(UpdateChecker.TOKEN_PROVIDER)) {
            builder.setNegativeButton(getString(R.string.Delete), (dialog, which) -> {
                AiKeyStorage.clear(UpdateChecker.TOKEN_PROVIDER);
                updateRows();
            });
        } else {
            builder.setNegativeButton(getString(R.string.Cancel), null);
        }
        builder.show();
    }

    // ── Будівельні блоки ─────────────────────────────────────────────────

    private void updateRows() {
        if (currentCell != null) {
            final String commit = UpdateChecker.currentCommit();
            currentCell.setTextAndValue(getString(R.string.UpdateCurrentRow),
                    TextUtils.isEmpty(commit) ? getString(R.string.UpdateUnknown) : commit, true);
        }
        if (checkCell != null) {
            checkCell.setText(getString(R.string.UpdateCheckRow), false);
        }
        if (tokenCell != null) {
            final String masked = AiKeyStorage.isAvailable()
                    ? AiKeyStorage.getMaskedKey(UpdateChecker.TOKEN_PROVIDER) : null;
            tokenCell.setTextAndValue(getString(R.string.UpdateTokenRow),
                    masked != null ? masked : getString(R.string.AiKeyNotSet), true);
            tokenCell.setEnabled(AiKeyStorage.isAvailable());
        }
    }

    private TextSettingsCell row(Context context, View.OnClickListener onClick) {
        final TextSettingsCell cell = new TextSettingsCell(context);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        if (onClick != null) {
            cell.setOnClickListener(onClick);
        }
        return cell;
    }

    private HeaderCell header(Context context, String text) {
        final HeaderCell cell = new HeaderCell(context);
        cell.setText(text);
        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return cell;
    }

    private TextInfoPrivacyCell info(Context context, CharSequence text) {
        final TextInfoPrivacyCell cell = new TextInfoPrivacyCell(context);
        cell.setText(text);
        return cell;
    }
}
