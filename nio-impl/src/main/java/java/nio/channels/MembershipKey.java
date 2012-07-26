/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package java.nio.channels;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * Compatibility stub.
 */
public abstract class MembershipKey {

    protected MembershipKey() {}

    public abstract boolean isValid();
    public abstract void drop();
    public abstract MembershipKey block(InetAddress source) throws IOException;
    public abstract MembershipKey unblock(InetAddress source);
    public abstract MulticastChannel channel();
    public abstract InetAddress group();
    public abstract NetworkInterface networkInterface();
    public abstract InetAddress sourceAddress();
}
