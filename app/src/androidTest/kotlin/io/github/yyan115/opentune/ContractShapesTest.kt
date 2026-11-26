package io.github.yyan115.opentune

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The comparison that LiveContractTest uses, with answers made up here, so that it runs everywhere. */
@RunWith(AndroidJUnit4::class)
class ContractShapesTest {
    private fun compare(expected: String, actual: String) = ContractShapes.compare("x.json", JSONObject(expected), JSONObject(actual))

    @Test fun answersWithTheSameFieldsAndOtherValuesHaveTheSameShape() {
        val (broken, added) = compare("""{"id":1,"title":"A","items":[{"ai":false}]}""", """{"id":99,"title":"B","items":[{"ai":true},{"ai":false}]}""")
        assertTrue(broken.isEmpty() && added.isEmpty())
    }

    @Test fun aFieldThatTheServiceNoLongerSendsBreaksTheContract() {
        val (broken, _) = compare("""{"id":1,"ai":false}""", """{"id":1}""")
        assertEquals(listOf("x.json: the field .ai is gone"), broken)
    }

    @Test fun aFieldInsideAListThatIsGoneIsFoundToo() {
        val (broken, _) = compare("""{"items":[{"item":{"ai":false,"id":1}}]}""", """{"items":[{"item":{"id":1}}]}""")
        assertEquals(listOf("x.json: the field .items[].item.ai is gone"), broken)
    }

    @Test fun aFieldThatChangedItsKindBreaksTheContract() {
        val (broken, _) = compare("""{"id":1}""", """{"id":"1"}""")
        assertEquals(1, broken.size)
        assertTrue(broken.single().contains("was [number] and is now [string]"))
    }

    @Test fun aNewFieldIsOnlyListed() {
        val (broken, added) = compare("""{"id":1}""", """{"id":1,"ai":false}""")
        assertTrue(broken.isEmpty())
        assertEquals(listOf("x.json: new field .ai"), added)
    }

    @Test fun nullFitsEveryKindBecauseAFieldIsEmptyInOneAnswerAndFilledInAnother() {
        assertTrue(compare("""{"version":null}""", """{"version":"Remastered"}""").first.isEmpty())
        assertTrue(compare("""{"version":"Remastered"}""", """{"version":null}""").first.isEmpty())
    }

    @Test fun anEmptyListKeepsItsFieldsOutOfTheLiveAnswerAndThatIsAFailureOnlyWhenTheFixtureHasThem() {
        assertEquals(1, compare("""{"items":[{"id":1}]}""", """{"items":[]}""").first.size)
        assertTrue(compare("""{"items":[]}""", """{"items":[{"id":1}]}""").first.isEmpty())
    }
}
