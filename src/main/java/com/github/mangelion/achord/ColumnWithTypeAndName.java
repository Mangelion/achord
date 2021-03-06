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

import io.netty.buffer.ByteBuf;

/**
 * @author Dmitriy Poluyanov
 * @since 24.12.2017
 */
final class ColumnWithTypeAndName {
    final byte type;
    final ByteBuf data;
    final String name;

    // todo may be lazy allocation with predefined size would be more effective
    ColumnWithTypeAndName(byte type, String name, ByteBuf data) {
        this.type = type;
        this.name = name;
        this.data = data;
    }
}
