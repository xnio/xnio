/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. 


package org.xnio.nio;

import org.xnio.Xnio;
import org.xnio.XnioProvider;

/**
 * The NIO XNIO provider.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NioXnioProvider implements XnioProvider {
    private static final Xnio INSTANCE = new NioXnio();

    /** {@inheritDoc} */
    public Xnio getInstance() {
        return INSTANCE;
    }

    /** {@inheritDoc} */
    public String getName() {
        return INSTANCE.getName();
    }
}
