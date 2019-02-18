package com.pbaranchikov.stash.checks;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nonnull;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.scope.Scope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
import com.atlassian.bitbucket.setting.SettingsValidator;

/**
 * Validator for hook settings.
 * @author Pavel Baranchikov
 */
public class HookSettingsValidator implements SettingsValidator {

    private final I18nService i18nService;

    public HookSettingsValidator(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @Override
    public void validate(@Nonnull Settings settings,
            @Nonnull SettingsValidationErrors errors, @Nonnull Scope scope) {
        final String excludedFiles = settings.getString(Constants.SETTING_EXCLUDED_FILES);
        validateExcludedFiles(errors, excludedFiles);
    }

    private void validateExcludedFiles(SettingsValidationErrors errors, String excludedString) {
        if (excludedString == null) {
            return;
        }
        final String[] excludedFiles = excludedString.split(Constants.PATTERNS_SEPARATOR);
        final Map<String, String> wrongPatterns = new HashMap<>();
        for (String patternString : excludedFiles) {
            try {
                Pattern.compile(patternString);
            } catch (PatternSyntaxException e) {
                wrongPatterns.put(patternString, e.getLocalizedMessage());
            }
        }
        if (wrongPatterns.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (Entry<String, String> entry : wrongPatterns.entrySet()) {
            sb.append(i18nService.getText("validator.excluded.pattern",
                    "Pattern {0} is invalid: {1}", entry.getKey(), entry.getValue()));
            sb.append("\n");
        }
        errors.addFieldError(Constants.SETTING_EXCLUDED_FILES, i18nService.getText(
                "validator.excluded.error.wrong.patterns", "Wrong excluded file string: {0}",
                sb.toString()));
    }
}
