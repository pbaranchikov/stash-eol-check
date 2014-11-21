package com.pbaranchikov.stash.checks;

import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.RepositorySettingsValidator;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import com.google.common.collect.Maps;

/**
 * Validator for hook settings.
 * @author Pavel Baranchikov
 */
public class HookSettingsValidator implements RepositorySettingsValidator {

    private final I18nService i18nService;

    public HookSettingsValidator(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @Override
    public void validate(Settings settings, SettingsValidationErrors errors, Repository repository) {
        final String excludedFiles = settings.getString(Constants.SETTING_EXCLUDED_FILES);
        validateExcludedFiles(errors, excludedFiles);
    }

    private void validateExcludedFiles(SettingsValidationErrors errors, String excludedString) {
        if (excludedString == null) {
            return;
        }
        final String[] excludedFiles = excludedString.split(Constants.PATTERNS_SEPARATOR);
        final Map<String, String> wrongPatterns = Maps.newHashMap();
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
