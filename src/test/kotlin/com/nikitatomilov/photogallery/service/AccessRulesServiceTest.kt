package com.nikitatomilov.photogallery.service

import com.nikitatomilov.photogallery.dto.Rule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessRulesServiceTest {

  private lateinit var service: AccessRulesService

  @Test
  fun parsesCorrectly() {
    //given/when
    val rules = service.getRules()
    //then
    assertThat(rules.defaultAction).isEqualTo(Rule.DENY)
    assertThat(rules.rules).hasSize(2)
    val user1rules = rules.rules[0]
    assertThat(user1rules.email).isEqualTo("1@1")
    assertThat(user1rules.action).isEqualTo(Rule.DENY)
    assertThat(user1rules.folderWhitelist).isEqualTo(listOf("/a/w1", "/a/w2"))
    assertThat(user1rules.folderBlacklist).isEqualTo(listOf("/a/b1", "/a/b2"))
    val user2rules = rules.rules[1]
    assertThat(user2rules.email).isEqualTo("2@2")
    assertThat(user2rules.action).isEqualTo(Rule.ALLOW)
    assertThat(user2rules.folderWhitelist).isEqualTo(listOf("/a/w3", "/a/w4"))
    assertThat(user2rules.folderBlacklist).isEqualTo(listOf("/a/b3", "/a/b4"))
  }

  @TestFactory
  fun detectsAllowActionCorrectly() : Collection<DynamicTest> {
    return listOf(
      TestCase("1@1", "/a/", false),
      TestCase("1@1", "/a/w1", true),
      TestCase("1@1", "/a/w2", true),
      TestCase("1@1", "/a/w3", false),
      TestCase("1@1", "/a/b1", false),
      TestCase("1@1", "/a/b2", false),
      TestCase("2@2", "/a/", true),
      TestCase("2@2", "/a/w1", true),
      TestCase("2@2", "/a/w2", true),
      TestCase("2@2", "/a/w3", true),
      TestCase("2@2", "/a/w4", true),
      TestCase("2@2", "/a/b1", true),
      TestCase("2@2", "/a/b2", true),
      TestCase("2@2", "/a/b3", false),
      TestCase("2@2", "/a/b4", false),
      TestCase("3@3", "/a/", false),
    ).map { DynamicTest.dynamicTest(it.toString()) {
      //given/when
      val actual = service.isAllowed(it.email, File(it.path))
      //then
      assertThat(actual).isEqualTo(it.expected)
    } }
  }

  @BeforeAll
  fun initService() {
    val file = Files.createTempFile("", ".yaml")
    Files.writeString(
        file, """
        defaultAction: DENY
        rules:
          - email: "1@1"
            action: DENY
            folderWhitelist: 
              - "/a/w1"
              - "/a/w2"
            folderBlacklist: 
              - "/a/b1"
              - "/a/b2"
          - email: "2@2"
            action: ALLOW
            folderWhitelist: 
              - "/a/w3"
              - "/a/w4"
            folderBlacklist: 
              - "/a/b3"
              - "/a/b4"
    """.trimIndent())
    service = AccessRulesService(file.absolutePathString())
    service.readRulesFile()
  }

  data class TestCase(
    val email: String,
    val path: String,
    val expected: Boolean
  ) {
    override fun toString(): String {
      return "$email - $path - $expected"
    }
  }
}