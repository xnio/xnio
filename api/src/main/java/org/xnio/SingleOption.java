package org.xnio;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class SingleOption<T> extends Option<T> {

    private static final long serialVersionUID = 2449094406108952764L;

    private transient final Class<T> type;
    private transient final ValueParser<T> parser;

    SingleOption(final Class<?> declClass, final String name, final Class<T> type) {
        super(declClass, name);
        this.type = type;
        parser = Option.getParser(type);
    }

    public T cast(final Object o) {
        return type.cast(o);
    }

    public T parseValue(final String string) throws IllegalArgumentException {
        return parser.parseValue(string);
    }
}
