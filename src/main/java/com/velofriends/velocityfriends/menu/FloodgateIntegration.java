package com.velofriends.velocityfriends.menu;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public final class FloodgateIntegration {
    private final Logger logger;
    private Object api;
    private boolean checked;

    public FloodgateIntegration(Logger logger) {
        this.logger = logger;
    }

    public boolean available() {
        ensureApi();
        return api != null;
    }

    public boolean isBedrock(Player player) {
        ensureApi();
        if (api == null) {
            return false;
        }
        try {
            Method method = api.getClass().getMethod("isFloodgatePlayer", UUID.class);
            Object result = method.invoke(api, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    public boolean sendSimpleForm(Player player, String title, String content, List<FormButton> buttons) {
        ensureApi();
        if (api == null) {
            return false;
        }
        try {
            Class<?> simpleForm = Class.forName("org.geysermc.cumulus.form.SimpleForm");
            Object builder = simpleForm.getMethod("builder").invoke(null);
            invoke(builder, "title", title);
            invoke(builder, "content", content);
            for (FormButton button : buttons) {
                invoke(builder, "button", button.label());
            }
            Consumer<Object> handler = response -> {
                int id = clickedButtonId(response);
                if (id >= 0 && id < buttons.size()) {
                    buttons.get(id).action().run();
                }
            };
            invokeConsumer(builder, "validResultHandler", handler);
            Object form = invokeResult(builder, "build");
            sendForm(player.getUniqueId(), form);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.debug("Unable to send Floodgate simple form", exception);
            return false;
        }
    }

    public boolean sendInputForm(Player player, String title, String label, String placeholder, Consumer<String> submit) {
        ensureApi();
        if (api == null) {
            return false;
        }
        try {
            Class<?> customForm = Class.forName("org.geysermc.cumulus.form.CustomForm");
            Object builder = customForm.getMethod("builder").invoke(null);
            invoke(builder, "title", title);
            invokeInput(builder, label, placeholder);
            Consumer<Object> handler = response -> submit.accept(readFirstInput(response));
            invokeConsumer(builder, "validResultHandler", handler);
            Object form = invokeResult(builder, "build");
            sendForm(player.getUniqueId(), form);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.debug("Unable to send Floodgate custom form", exception);
            return false;
        }
    }

    private void ensureApi() {
        if (checked) {
            return;
        }
        checked = true;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            logger.info("Floodgate detected; Bedrock forms are enabled when configured.");
        } catch (ReflectiveOperationException exception) {
            api = null;
            logger.info("Floodgate not detected; using Java chat menus for all players.");
        }
    }

    private void sendForm(UUID uuid, Object form) throws ReflectiveOperationException {
        if (trySendForm(api, uuid, form)) {
            return;
        }
        Object floodgatePlayer = null;
        try {
            floodgatePlayer = invokeResult(api, "getPlayer", uuid);
        } catch (NoSuchMethodException ignored) {
            // Older Floodgate versions expose only FloodgateApi#sendForm.
        }
        if (floodgatePlayer != null && trySendForm(floodgatePlayer, form)) {
            return;
        }
        throw new NoSuchMethodException("FloodgateApi#sendForm(UUID, Form)");
    }

    private static boolean trySendForm(Object target, Object... args) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals("sendForm") || !parametersCompatible(method, args)) {
                continue;
            }
            method.invoke(target, args);
            return true;
        }
        return false;
    }

    private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
        invokeResult(target, name, args);
        return target;
    }

    private static Object invokeResult(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && parametersCompatible(method, args)) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void invokeConsumer(Object target, String name, Consumer<Object> consumer) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && parametersCompatible(method, consumer)) {
                method.invoke(target, consumer);
                return;
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static void invokeInput(Object target, String label, String placeholder) throws ReflectiveOperationException {
        List<String> values = new ArrayList<>();
        values.add(label);
        values.add(placeholder);
        values.add("");
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals("input")) {
                continue;
            }
            int count = method.getParameterCount();
            if (count >= 1 && count <= 3) {
                Object[] args = values.subList(0, count).toArray();
                if (parametersCompatible(method, args)) {
                    method.invoke(target, args);
                    return;
                }
            }
        }
        throw new NoSuchMethodException("input");
    }

    private static int clickedButtonId(Object response) {
        Object formResponse = unwrapResponse(response);
        if (formResponse == null) {
            return -1;
        }
        try {
            Object result = formResponse.getClass().getMethod("clickedButtonId").invoke(formResponse);
            return result instanceof Number number ? number.intValue() : -1;
        } catch (ReflectiveOperationException exception) {
            return -1;
        }
    }

    private static String readFirstInput(Object response) {
        Object formResponse = unwrapResponse(response);
        if (formResponse == null) {
            return "";
        }
        try {
            Object result = formResponse.getClass().getMethod("asInput", int.class).invoke(formResponse, 0);
            return result == null ? "" : String.valueOf(result);
        } catch (ReflectiveOperationException ignored) {
            try {
                Object result = formResponse.getClass().getMethod("next").invoke(formResponse);
                return result == null ? "" : String.valueOf(result);
            } catch (ReflectiveOperationException ignoredAgain) {
                return "";
            }
        }
    }

    private static Object unwrapResponse(Object response) {
        if (response == null) {
            return null;
        }
        try {
            Object result = response.getClass().getMethod("response").invoke(response);
            return result == null ? response : result;
        } catch (ReflectiveOperationException ignored) {
            return response;
        }
    }

    private static boolean parametersCompatible(Method method, Object... args) {
        if (method.getParameterCount() != args.length) {
            return false;
        }
        Class<?>[] types = method.getParameterTypes();
        for (int index = 0; index < args.length; index++) {
            Object arg = args[index];
            if (arg != null && !wrap(types[index]).isAssignableFrom(arg.getClass())) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    public record FormButton(String label, Runnable action) {
    }
}
