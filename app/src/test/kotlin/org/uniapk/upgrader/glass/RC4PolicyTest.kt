package org.uniapk.upgrader.glass

import android.app.Application
import androidx.compose.ui.graphics.Color
import com.kryptoprefs.preferences.KryptoBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.uniapk.upgrader.data.ui.*
import org.uniapk.upgrader.prefs.Prefs

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[37],application=Application::class)
class RC4PolicyTest {
    private fun prefs()=Prefs(KryptoBuilder.nocrypt(RuntimeEnvironment.getApplication(),"rc4-${java.util.UUID.randomUUID()}"),false)
    private fun update(code:Long=200,name:String="2.0")=AppUpdate("Fixture","org.example.fixture",name,"1.0",code,100,GitHubSource)
    @Test fun onlyTheSelectedBuildIsSkippedAcrossSources(){val p=prefs();SkippedVersions.skip(update(),p);assertFalse(SkippedVersions.allowed(update().copy(source=ApkPureSource),p));assertTrue(SkippedVersions.allowed(update(201),p));assertTrue(SkippedVersions.allowed(update().copy(packageName="org.example.other"),p))}
    @Test fun higherBuildWithIdenticalDisplayVersionIsOffered(){val p=prefs();SkippedVersions.skip(update(200,"2.0"),p);assertTrue(SkippedVersions.allowed(update(201,"2.0"),p));assertFalse(SkippedVersions.allowed(update(200,"2.0 (store label)"),p))}
    @Test fun missingCodesFallBackToTheSpecificVersionName(){val p=prefs();SkippedVersions.skip(update(0,"V2.0"),p);assertFalse(SkippedVersions.allowed(update(0,"  v2.0  "),p));assertTrue(SkippedVersions.allowed(update(0,"2.1"),p));assertFalse(SkippedVersions.allowed(update(200,"2.0"),p))}
    @Test fun typedSkipsSurvivePreferenceReloadAndCanBeRestored(){val p=prefs();SkippedVersions.skip(update(),p);assertEquals(listOf(SkippedVersions.entry(update())),p.skippedReleases.get());SkippedVersions.restore(SkippedVersions.entries(p).single(),p);assertTrue(SkippedVersions.allowed(update(),p))}
    @Test fun legacyHiddenEntriesRemainVisibleAndRestorable(){val p=prefs();p.ignoredReleases.put(listOf("org.example.fixture|2.0"));p.ignoredVersions.put(listOf(17));assertFalse(SkippedVersions.allowed(update(),p));val entry=SkippedVersions.entries(p).single();assertEquals(0L,entry.code);SkippedVersions.restore(entry,p);assertTrue(SkippedVersions.allowed(update(),p));assertEquals(listOf(17),p.ignoredVersions.get())}
    @Test fun wholeAppIgnoreIsIndependentOfSkippingOneBuild(){val p=prefs();SkippedVersions.skip(update(),p);assertTrue(AppExclusions.allowed(update().packageName,p));p.ignoredApps.put(listOf(update().packageName));assertFalse(AppExclusions.allowed(update(201).packageName,p));SkippedVersions.restore(SkippedVersions.entry(update()),p);assertFalse(AppExclusions.allowed(update().packageName,p))}
    @Test fun repeatedSkipDoesNotCreateDuplicates(){val p=prefs();repeat(5){SkippedVersions.skip(update(),p)};assertEquals(1,p.skippedReleases.get().size)}
    @Test fun legacyCustomAccentBecomesTheSingleSeed(){val p=prefs();p.customAccent.put(true);p.accentColor.put(0xff23bba1);p.customBackground.put(true);p.backgroundColor.put(0xff777777);val a=AppearanceStore(p).state.value;assertTrue(a.customPalette);assertEquals(0xff23bba1L,a.paletteSeed);assertFalse(p.customAccent.get());assertFalse(p.customBackground.get());assertEquals(a,AppearanceStore(p).state.value)}
    @Test fun legacyCustomBackgroundAloneIsRetainedAsSeed(){val p=prefs();p.customBackground.put(true);p.backgroundColor.put(0xffaabbcc);val a=AppearanceStore(p).state.value;assertEquals(0xffaabbccL,a.paletteSeed);assertTrue(a.customPalette)}
    @Test fun newPalettePersistsAndResetKeepsUpdatePreferences(){val p=prefs();val store=AppearanceStore(p);SkippedVersions.skip(update(),p);store.update{it.copy(customPalette=true,paletteSeed=0xff77aa00)};assertEquals(store.state.value,AppearanceStore(p).state.value);store.reset();assertEquals(Appearance(),AppearanceStore(p).state.value);assertFalse(SkippedVersions.allowed(update(),p))}
    @Test fun everyPaletteForegroundMeetsContrastInEveryTheme(){val seeds=listOf(Color.Black,Color.White,Color.Red,Color.Green,Color.Blue,Color.Yellow,Color(0xff777777))+AppearancePalettes.entries.map{Color(it.accent)}
        for(seed in seeds)for(mode in 0..3){val c=PaletteModel.scheme(seed,mode!=0,mode==1,mode==3)
            for((fg,bg) in listOf(c.onPrimary to c.primary,c.onSecondary to c.secondary,c.onTertiary to c.tertiary,c.onBackground to c.background,c.onSurface to c.surface,c.onSurfaceVariant to c.surface,c.onPrimaryContainer to c.primaryContainer,c.onSecondaryContainer to c.secondaryContainer,c.primary to c.surface))assertTrue("Low contrast: seed=$seed mode=$mode fg=$fg bg=$bg",PaletteModel.contrast(fg,bg)>=4.49f)
        }
    }
    @Test fun changingSeedChangesButtonsSurfacesAndTintsTogether(){val a=PaletteModel.scheme(Color(0xff4b53ff),true);val b=PaletteModel.scheme(Color(0xffff5f34),true);assertNotEquals(a.primary,b.primary);assertNotEquals(a.secondary,b.secondary);assertNotEquals(a.surface,b.surface);assertNotEquals(a.background,b.background);assertNotEquals(a.primaryContainer,b.primaryContainer)}
    @Test fun oledAlwaysHasAnExactlyBlackBase(){for(p in AppearancePalettes.entries)assertEquals(Color.Black,PaletteModel.scheme(Color(p.accent),true,oled=true).background)}
    @Test fun flowingWaveActuallyChangesItsGeometryAndLoops(){for(x in listOf(.1f,.27f,.63f)){assertNotEquals(WaveMotion.offset(x,0f),WaveMotion.offset(x,.3f));assertEquals(WaveMotion.offset(x,0f),WaveMotion.offset(x,1f),.0001f)}}
    @Test fun navigationHasFourCategoriesAndOnlyValidStartViews(){assertEquals(4,SettingsCategory.entries.size);assertFalse(SettingsCategory.entries.any{it.title=="Schnellzugriff"});assertEquals("overview",StartViews.valid("settings"));assertEquals("apps",StartViews.valid("apps"));assertEquals(0,UniMotion.duration(false))}
}
