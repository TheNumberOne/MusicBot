/*
 * Copyright 2020 Rosetta Roberts
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package io.github.thenumberone.discord.musicbot

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrameBufferFactory
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer
import discord4j.core.DiscordClientBuilder
import discord4j.core.event.domain.message.MessageCreateEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking

val playerManager = DefaultAudioPlayerManager().apply {
    configuration.frameBufferFactory = AudioFrameBufferFactory(::NonAllocatingAudioFrameBuffer)
    AudioSourceManagers.registerRemoteSources(this)
}
val player: AudioPlayer = playerManager.createPlayer()
val provider = LavaPlayerAudioProvider(player)
val scheduler = TrackScheduler(player)

private val commands = mutableMapOf<String, Command>().apply {
    put("ping") { event ->
        val channel = event.message.channel.awaitSingle()
        channel.createMessage("Pong!").awaitSingle()
    }
    put("join") { event ->
        val member = event.member.orElse(null) ?: return@put
        val voiceState = member.voiceState.awaitFirstOrNull() ?: return@put
        val channel = voiceState.channel.awaitFirstOrNull() ?: return@put
        channel.join { spec -> spec.setProvider(provider) }.awaitFirstOrNull()
    }
    put("play") { event ->
        val content = event.message.content.orElse(null) ?: return@put
        val command = content.split(" ")
        val argument = command[1]
        playerManager.loadItem(argument, scheduler)
    }
}

fun main(args: Array<String>) {
    val client = DiscordClientBuilder.create(args[0]).build()

    runBlocking {
        launch {
            client.eventDispatcher.on(MessageCreateEvent::class.java)
                .asFlow()
                .collect { event ->
                    val content = event.message.content.orElse(null) ?: return@collect
                    val (_, command) = commands.entries
                        .singleOrNull() { (key, _) -> content.startsWith("!$key") }
                        ?: return@collect
                    command(event)
                }
        }

        client.login().awaitSingle()
    }
}