package io.github.mangomaner.mangobot.model.onebot.event;

/**
 * Event interface for all OneBot events.
 */
public interface Event {
    long getTime();
    long getSelfId();
    String getPostType();
}
