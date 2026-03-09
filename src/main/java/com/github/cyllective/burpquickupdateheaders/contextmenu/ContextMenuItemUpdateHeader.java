package com.github.cyllective.burpquickupdateheaders.contextmenu;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import com.github.cyllective.burpquickupdateheaders.settingsmenu.SettingsMenuHeaderFilter;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static java.util.Collections.emptyList;

public class ContextMenuItemUpdateHeader implements ContextMenuItemsProvider {
    private final MontoyaApi api;

    public ContextMenuItemUpdateHeader(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        if (!isValidContext(event)) {
            return emptyList();
        }

        MessageEditorHttpRequestResponse selectedRequestResponse = event.messageEditorRequestResponse().get();
        List<HttpHeader> currentHeaders = selectedRequestResponse.requestResponse().request().headers();

        if (currentHeaders == null || currentHeaders.isEmpty()) {
            return emptyList();
        }

        String hostValue = selectedRequestResponse.requestResponse().request().headerValue("Host");
        if (hostValue == null) {
            return emptyList();
        }

        List<Component> listMenuItems = new ArrayList<>();
        for (HttpHeader header : currentHeaders) {
            if (SettingsMenuHeaderFilter.isHeaderInSelection(header.name())) {
                JMenuItem menuItem = new JMenuItem(header.name());
                menuItem.addActionListener(e -> {
                    Thread thread = new Thread(
                            () -> updateHeaderFromHistory(header.name(), hostValue, selectedRequestResponse),
                            "QuickUpdateHeaders-worker"
                    );
                    thread.setDaemon(true);
                    thread.start();
                });

                listMenuItems.add(menuItem);
            }
        }

        return listMenuItems;
    }

    private boolean isValidContext(ContextMenuEvent event) {
        return (event.isFromTool(ToolType.REPEATER) || event.isFromTool(ToolType.INTRUDER)) &&
                event.messageEditorRequestResponse().isPresent();
    }

    private void updateHeaderFromHistory(String headerName, String hostValue,
                                         MessageEditorHttpRequestResponse selectedResponse) {

        // It is faster (in big projects) to go through the whole history
        // rather than to filter the history first by host header
        List<ProxyHttpRequestResponse> history = api.proxy().history();
        if (history.isEmpty()) {
            return;
        }

        ListIterator<ProxyHttpRequestResponse> historyIterator =
                history.listIterator(history.size());

        while (historyIterator.hasPrevious()) {
            // Go through the list in reverse as we want the newest entries first
            ProxyHttpRequestResponse historyEntry = historyIterator.previous();

            HttpHeader hostHeader = historyEntry.request().header("Host");
            if (hostHeader == null) {
                continue;
            }

            if (hostHeader.value().equals(hostValue)) {
                // We are on the same host
                HttpHeader headerToInsert = historyEntry.request().header(headerName);
                if (headerToInsert != null) {
                    // Update the request with the newest header
                    selectedResponse.setRequest(
                            selectedResponse.requestResponse().request().withHeader(headerToInsert));
                    break; // Only update with the first (most recent) match
                }
            }
        }
    }
}