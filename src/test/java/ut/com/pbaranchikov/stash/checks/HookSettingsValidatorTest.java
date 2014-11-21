package ut.com.pbaranchikov.stash.checks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.stash.i18n.I18nService;
import com.atlassian.stash.repository.Repository;
import com.atlassian.stash.setting.Settings;
import com.atlassian.stash.setting.SettingsValidationErrors;
import com.pbaranchikov.stash.checks.Constants;
import com.pbaranchikov.stash.checks.HookSettingsValidator;

/**
 * Unit test for hook settings form validator {@link HookSettingsValidator}.
 * @author Pavel Baranchikov
 */
public class HookSettingsValidatorTest {

    private HookSettingsValidator validator;
    private Repository repo;
    private Settings settings;
    private SettingsValidationErrors errors;

    @Before
    public void createValidator() {
        final I18nService i18nService = Mockito.mock(I18nService.class);
        validator = new HookSettingsValidator(i18nService);
        repo = Mockito.mock(Repository.class);
        settings = Mockito.mock(Settings.class);
        errors = Mockito.mock(SettingsValidationErrors.class);
    }

    private void setPattern(String pattern) {
        Mockito.when(settings.getString(Constants.SETTING_EXCLUDED_FILES)).thenReturn(pattern);
    }

    @Test
    public void testEmpty() {
        validator.validate(settings, errors, repo);
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSimpleCorrect() {
        setPattern("file");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSimpleInCorrect() {
        setPattern("file\\");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralSimpleCorrect() {
        setPattern("file,file1,file2,file4");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSeveralSimpleInCorrect() {
        setPattern("file,file1,file2,file4\\");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralReCorrect() {
        setPattern("file.*,file1,file2,file4");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSeveralReInCorrect() {
        setPattern("file.*\\,file1,file2,file4");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralReInCorrect2() {
        setPattern("file.*,file1\\,file2,file4");
        validator.validate(settings, errors, repo);
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

}
