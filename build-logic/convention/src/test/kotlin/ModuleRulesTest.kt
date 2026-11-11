import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleRulesTest {
    @Test
    fun coreMayDependOnCore() {
        assertNull(ModuleRules.violations(":shared:core:network", ":shared:core:common"))
    }

    @Test
    fun coreMayNotDependOutsideCore() {
        assertNotNull(ModuleRules.violations(":shared:core:common", ":shared:playback"))
    }

    @Test
    fun platformsNeverDependOnEachOther() {
        assertNotNull(ModuleRules.violations(":shared:platform:twitch", ":shared:platform:kick"))
        assertNotNull(ModuleRules.violations(":shared:platform:kick", ":shared:platform:twitch"))
    }

    @Test
    fun featureNeverDependsOnConcretePlatform() {
        assertNotNull(ModuleRules.violations(":shared:feature:player", ":shared:platform:twitch"))
        assertNotNull(ModuleRules.violations(":shared:feature:player", ":shared:platform:kick"))
    }

    @Test
    fun featureMayDependOnPlatformApi() {
        assertNull(ModuleRules.violations(":shared:feature:player", ":shared:platform:api"))
    }

    @Test
    fun androidUiMayNotReachIntoSharedNonFeature() {
        assertNotNull(ModuleRules.violations(":androidApp:feature-ui:player", ":shared:chat"))
    }

    @Test
    fun androidUiMayDependOnDesignsystemAndFeatures() {
        assertNull(ModuleRules.violations(":androidApp:feature-ui:player", ":androidApp:designsystem"))
        assertNull(ModuleRules.violations(":androidApp:feature-ui:player", ":shared:feature:player"))
    }

    @Test
    fun appBindsConcretePlatforms() {
        assertNull(ModuleRules.violations(":androidApp:app", ":shared:platform:twitch"))
    }
}
