package com.jtransc.template

import org.junit.Assert
import org.junit.Test

class MinitemplateTest {
	@Test fun testDummy() {
		Assert.assertEquals("hello", Minitemplate("hello")(null))
	}

	@Test fun testSimple() {
		Assert.assertEquals("hello soywiz", Minitemplate("hello {{ name }}")(mapOf("name" to "soywiz")))
		Assert.assertEquals("soywizsoywiz", Minitemplate("{{name}}{{ name }}")(mapOf("name" to "soywiz")))
	}

	@Test fun testFor() {
		Assert.assertEquals("123", Minitemplate("{% for n in numbers %}{{ n }}{% end %}")(mapOf("numbers" to listOf(1, 2, 3))))
	}

	@Test fun testSimpleIf() {
		Assert.assertEquals("true", Minitemplate("{% if cond %}true{% else %}false{% end %}")(mapOf("cond" to 1)))
		Assert.assertEquals("false", Minitemplate("{% if cond %}true{% else %}false{% end %}")(mapOf("cond" to 0)))
		Assert.assertEquals("true", Minitemplate("{% if cond %}true{% end %}")(mapOf("cond" to 1)))
		Assert.assertEquals("", Minitemplate("{% if cond %}true{% end %}")(mapOf("cond" to 0)))
	}

	@Test fun testEval() {
		Assert.assertEquals("-5", Minitemplate("{{ -(1 + 4) }}")(null))
		Assert.assertEquals("false", Minitemplate("{{ 1 == 2 }}")(null))
		Assert.assertEquals("true", Minitemplate("{{ 1 < 2 }}")(null))
		Assert.assertEquals("true", Minitemplate("{{ 1 <= 1 }}")(null))
	}

	@Test fun testForAccess() {
		Assert.assertEquals("ZardBallesteros", Minitemplate("{% for n in persons %}{{ n.surname }}{% end %}")(mapOf("persons" to listOf(Person("Soywiz", "Zard"), Person("Carlos", "Ballesteros")))))
		Assert.assertEquals("ZardBallesteros", Minitemplate("{% for n in persons %}{{ n['sur'+'name'] }}{% end %}")(mapOf("persons" to listOf(Person("Soywiz", "Zard"), Person("Carlos", "Ballesteros")))))
	}

	data class Person(val name:String, val surname:String)
}