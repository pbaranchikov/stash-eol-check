package ut.com.pbaranchikov.stash.checks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.atlassian.bitbucket.i18n.I18nService;
import com.atlassian.bitbucket.i18n.SimpleI18nService;
import com.atlassian.bitbucket.i18n.SimpleI18nService.Mode;
import com.atlassian.bitbucket.repository.Repository;
import com.atlassian.bitbucket.scope.RepositoryScope;
import com.atlassian.bitbucket.setting.Settings;
import com.atlassian.bitbucket.setting.SettingsValidationErrors;
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
        final I18nService i18nService = new SimpleI18nService(Mode.RETURN_KEYS_WITH_ARGUMENTS);
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
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSimpleCorrect() {
        setPattern("file");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSimpleInCorrect() {
        setPattern("file\\");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralSimpleCorrect() {
        setPattern("file,file1,file2,file4");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSeveralSimpleInCorrect() {
        setPattern("file,file1,file2,file4\\");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralReCorrect() {
        setPattern("file.*,file1,file2,file4");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors, Mockito.never()).addFieldError(Mockito.anyString(),
                Mockito.anyString());
    }

    @Test
    public void testSeveralReInCorrect() {
        setPattern("file.*\\,file1,file2,file4");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    public void testSeveralReInCorrect2() {
        setPattern("file.*,file1\\,file2,file4");
        validator.validate(settings, errors, new RepositoryScope(repo));
        Mockito.verify(errors).addFieldError(Mockito.anyString(), Mockito.anyString());
    }

}
