package org.uniapk.upgrader.glass
import android.app.LocaleManager
import org.uniapk.upgrader.prefs.Prefs
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=android.app.Application::class)
class AppLanguagesTest {
 @Test fun nativeLanguageChoiceAndSystemDefaultAreApplied(){val c=RuntimeEnvironment.getApplication();val p=Prefs(KryptoBuilder.nocrypt(c,"language-test"),false);val store=AppLanguageStore(p);store.set(c,"en");assertEquals("en",c.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags());store.set(c,"SYSTEM");assertTrue(c.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty)}
 @Test fun coreEnglishLabelsAndHelpAreTranslated(){assertEquals("Settings",UiEnglish.translate("Einstellungen","en"));assertEquals("Check all available APKs",UiEnglish.translate("Alle verfügbaren APKs prüfen","en"));assertTrue(UiEnglish.translate(HelpText.BLOCKED,"en").startsWith("Blocked means"));assertEquals("Einstellungen",UiEnglish.translate("Einstellungen","de"))}
 @Test fun undeclaredLanguagesAreNotPretendedAsTranslated(){val c=RuntimeEnvironment.getApplication();val p=Prefs(KryptoBuilder.nocrypt(c,"language-test"),false);try{AppLanguageStore(p).set(c,"xx");fail("Unsupported language")}catch(_:IllegalArgumentException){}}
}
