/*
 * Copyright 2017-2018 Mangelion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.mangelion.achord;

import com.github.mangelion.achord.Settings.SettingCompressionMethod;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;

import java.util.concurrent.Flow;

import static com.github.mangelion.achord.ClickHousePacketEncoder.CLICK_HOUSE_PACKET_ENCODER;
import static com.github.mangelion.achord.Settings.NETWORK_COMPRESSION_METHOD;
import static com.github.mangelion.achord.internal.NetworkBootstrap.tryNative;
import static io.netty.channel.ChannelOption.TCP_NODELAY;

/**
 * @author Dmitriy Poluyanov
 * @since 10/02/2018
 */
public final class ClickHouseClient implements AutoCloseable {
    static final int COMPATIBLE_CLIENT_REVISION = 54327;
    static final String PACKET_DECODER = "decoder";
    static final String BLOCK_ENCODER = "blockEncoder";
    static final String PACKET_ENCODER = "encoder";

    private final Bootstrap b;
    private final EventLoopGroup workersGroup;
    private final EventLoopGroup compressionGroup;
    private String database;
    private String username = database = "default";
    private String password = "";
    private Settings settings = new Settings();
    private Limits limits = new Limits();
    private CompressionMethod compressionMethod;
    private boolean strictNative = false;

    public ClickHouseClient() {
        // todo make № of threads customizable, like whole group
        workersGroup = new DefaultEventLoopGroup(2);
        compressionGroup = new DefaultEventLoopGroup(2);
        b = new Bootstrap()
                // defaults, can be overridden
                .remoteAddress("localhost", 9000)
                // todo make configurable, because on macosx there are no clashes with Nagle's Algorithm
                .option(TCP_NODELAY, true)
                .handler(new ChannelInitializer() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline()
                                // decoders
                                .addFirst(PACKET_DECODER, new ClickHousePacketDecoder())
                                .addLast(workersGroup, "messageHandler", ClickHouseServerMessageHandler.CLICK_HOUSE_SERVER_MESSAGE_HANDLER)
                                // encoders
                                .addFirst(PACKET_ENCODER, CLICK_HOUSE_PACKET_ENCODER)
                                .addFirst(BLOCK_ENCODER, DataBlockEncoder.DATA_BLOCK_ENCODER);
                    }
                });
    }

    /**
     * Creates new client for ClickHouse server
     *
     * @return created client
     */
    static ClickHouseClient bootstrap() {
        return new ClickHouseClient();
    }

    public ClickHouseClient address(String inetHost, int port) {
        b.remoteAddress(inetHost, port);
        return this;
    }

    public ClickHouseClient database(String database) {
        this.database = database;
        return this;
    }

    public ClickHouseClient username(String username) {
        this.username = username;
        return this;
    }

    public ClickHouseClient password(String password) {
        this.password = password;
        return this;
    }

    public ClickHouseClient compression(CompressionMethod method) {
        this.settings.put(NETWORK_COMPRESSION_METHOD, new SettingCompressionMethod(method));
        return this;
    }

    public ClickHouseClient strictNativeNetwork(boolean strictNative) {
        this.strictNative = strictNative;
        return this;
    }

    public <T> Flow.Publisher<Void> sendData(String query, Flow.Publisher<T[]> source) {
        return this.sendData("", query, source);
    }

    /**
     * Reactive way for sending data.
     * Data object {@code T[]} collects from upstream and buffers for sending latter in huge blocks
     *
     * @param queryId CH query identifier
     * @param query   CH query description of inserted data
     * @param source  reactive data publisher
     * @param <T>     type of incoming object array
     * @return empty {@code <Void>} publisher that signals success or error after insert process ends
     */
    public <T> Flow.Publisher<Void> sendData(String queryId, String query, Flow.Publisher<T[]> source) {
        query += " FORMAT Native";
        AuthData authData = new AuthData(database, username, password);
        return new EmptyResponsePublisher<>(
                prepareBootstrap(b, strictNative), workersGroup, compressionGroup, authData, queryId, query, settings, limits, source);
    }

    private static Bootstrap prepareBootstrap(Bootstrap b, boolean strictNative) {
        Bootstrap clone = b.clone();

        if (tryNative(clone)) {
            return clone;
        } else if (strictNative) {
            throw new IllegalStateException("Strict native network mode is enabled, " +
                    "but attempt to enable native mode was failed");
        } else {
            // fallback to Java.NIO
            return clone.group(new NioEventLoopGroup())
                    .channel(NioSocketChannel.class);
        }
    }

    @Override
    public void close() {
        Future<?> configShutdown = b.config().group().shutdownGracefully();
        Future<?> decompressingShutdown = workersGroup.shutdownGracefully();

        configShutdown.syncUninterruptibly();
        decompressingShutdown.syncUninterruptibly();
    }
}
