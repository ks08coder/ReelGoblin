package com.reelgoblin

import kotlin.random.Random

object Lines {
    fun stageOf(n: Int) = when {
        n < 5 -> 0
        n < 10 -> 1
        n < 20 -> 2
        n < 35 -> 3
        n < 50 -> 4
        else -> 5
    }

    private val milestones = mapOf(
        1 to "Reel #1! Nom nom 🍪",
        3 to "Three already? I'm just warming up 😏",
        5 to "5 reels! I'm getting chunky 🥔",
        10 to "10 reels... your thumb needs a hug 👍",
        15 to "15! Fun fact: blinking is free 👀",
        20 to "20 reels. I ate a whole sandwich of your time 🥪",
        25 to "25! Drink some water, human 💧",
        30 to "30 reels! I can see my belly from here",
        40 to "40... I'm bigger than your phone now 📱",
        50 to "50 REELS. I have become GIANT 👹",
        75 to "75?! Your couch is filing for divorce 🛋️",
        100 to "100. Legend. Please go outside. 🌳"
    )

    private val pool = listOf(
        listOf("Feed me reels, I'm tiny 🍼", "Hehe, hi!", "I like it here ✨"),
        listOf("Mmm, scrolly snacks 😋", "Your thumb is so fast 🏎️", "Burp. Excuse me."),
        listOf("I'm getting hungry for more 👀", "Blink twice if you're lost 😵", "Is it still the same day?"),
        listOf("I'm kinda big now, huh", "My belly is a reel counter 🎞️", "Seriously, stretch your neck 🦒"),
        listOf("I can't fit in the screen!", "You + me = 1 big problem 😅", "Say no to reel number next."),
        listOf("I AM THE REEL MONSTER 👹", "ROAR. Go outside. ROAR.", "Your phone is warm. So am I. Stop.")
    )

    private val home = listOf("Enough! Touch some grass 🌱", "Bedtime for your thumbs 🌙", "Bye bye reels! 👋")
    private val tickle = listOf("Hehe that tickles! 🤭", "Boop! 👉", "Stop it, I'm counting!", "I'm watching you 👀")

    fun forCount(n: Int, goal: Int): String? {
        if (goal > 0 && n == goal) return "That's your goal! Wanna stop here? 🥺"
        if (goal > 0 && n == goal + 1) return "Goal crossed. I'm not mad, just disappointed 😤"
        milestones[n]?.let { return it }
        if (n % 5 == 0) return pool[stageOf(n)].random()
        return null
    }

    fun randomHome() = home[Random.nextInt(home.size)]
    fun tickle() = tickle[Random.nextInt(tickle.size)]
}
