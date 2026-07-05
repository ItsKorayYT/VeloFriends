package com.velofriends.velocityfriends.model;

import java.util.UUID;

public record PlayerSettings(
        UUID playerUuid,
        boolean friendRequestsEnabled,
        PrivacyLevel dmPrivacy,
        PrivacyLevel onlineStatusVisibility,
        ServerVisibility serverVisibility,
        boolean messagesEnabled,
        boolean socialSpy,
        boolean friendNotifications,
        boolean actionbarDm,
        boolean soundDm
) {
    public PlayerSettings withFriendRequestsEnabled(boolean enabled) {
        return new PlayerSettings(playerUuid, enabled, dmPrivacy, onlineStatusVisibility, serverVisibility,
                messagesEnabled, socialSpy, friendNotifications, actionbarDm, soundDm);
    }

    public PlayerSettings withDmPrivacy(PrivacyLevel privacy) {
        return new PlayerSettings(playerUuid, friendRequestsEnabled, privacy, onlineStatusVisibility, serverVisibility,
                messagesEnabled, socialSpy, friendNotifications, actionbarDm, soundDm);
    }

    public PlayerSettings withOnlineStatusVisibility(PrivacyLevel privacy) {
        return new PlayerSettings(playerUuid, friendRequestsEnabled, dmPrivacy, privacy, serverVisibility,
                messagesEnabled, socialSpy, friendNotifications, actionbarDm, soundDm);
    }

    public PlayerSettings withServerVisibility(ServerVisibility visibility) {
        return new PlayerSettings(playerUuid, friendRequestsEnabled, dmPrivacy, onlineStatusVisibility, visibility,
                messagesEnabled, socialSpy, friendNotifications, actionbarDm, soundDm);
    }

    public PlayerSettings withMessagesEnabled(boolean enabled) {
        return new PlayerSettings(playerUuid, friendRequestsEnabled, dmPrivacy, onlineStatusVisibility, serverVisibility,
                enabled, socialSpy, friendNotifications, actionbarDm, soundDm);
    }

    public PlayerSettings withSocialSpy(boolean enabled) {
        return new PlayerSettings(playerUuid, friendRequestsEnabled, dmPrivacy, onlineStatusVisibility, serverVisibility,
                messagesEnabled, enabled, friendNotifications, actionbarDm, soundDm);
    }
}
