package com.aviary.android.feather.sdk;

import android.util.Pair;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.sdk.panels.AbstractPanelLoaderService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by alessandro on 29/06/14.
 */
public class ToolsLoaderAsyncTask extends AviaryAsyncTask<FeatherActivity, Void, ToolsLoaderAsyncTask.ToolLoadResult> {
    public interface OnToolsLoadListener {
        /**
         * Finished loading the list of available tools, this method is called
         * in the UI thread
         *
         * @param toolNames  list of available tools
         * @param entries    list of corresponding entries
         * @param whiteLabel white-label is enabled
         */
        void onToolsLoaded(List<String> toolNames, List<ToolEntry> entries, boolean whiteLabel);
    }

    static class ToolLoadResult {
        public List<String>    tools;
        public List<ToolEntry> entries;
        public boolean         whiteLabel;
    }

    private final OnToolsLoadListener mListener;
    private final List<String>        mToolList;

    ToolsLoaderAsyncTask(final OnToolsLoadListener listener, final List<String> toolList) {
        this.mListener = listener;
        this.mToolList = toolList;
    }

    @Override
    protected ToolLoadResult doInBackground(final FeatherActivity... params) {
        final FeatherActivity activity = params[0];
        if (null == activity) {
            return null;
        }

        final Pair<List<String>, List<ToolEntry>> tools = loadTools(activity, mToolList);
        final List<String> permissions = CdsUtils.getPermissions(activity);

        ToolLoadResult result = new ToolLoadResult();
        result.tools = tools.first;
        result.entries = tools.second;
        result.whiteLabel = permissions != null && permissions.contains(AviaryCds.Permission.whitelabel.name());
        return result;
    }

    @Override
    protected void doPreExecute() {}

    @Override
    protected void doPostExecute(final ToolLoadResult toolLoadResult) {
        if (null != mListener) {
            if (null != toolLoadResult) {
                mListener.onToolsLoaded(toolLoadResult.tools, toolLoadResult.entries, toolLoadResult.whiteLabel);
            }
        }
    }

    Pair<List<String>, List<ToolEntry>> loadTools(final FeatherActivity activity, List<String> currentList) {
        if (null == currentList) {
            currentList = activity.loadStandaloneTools();

            if (null == currentList) {
                currentList = Arrays.asList(ToolLoaderFactory.getDefaultTools());
            }
        }

        List<ToolEntry> listEntries = new ArrayList<ToolEntry>();
        Map<String, ToolEntry> entryMap = new HashMap<String, ToolEntry>();
        ToolEntry[] allEntries = AbstractPanelLoaderService.getToolsEntries();

        for (int i = 0; i < allEntries.length; i++) {
            ToolLoaderFactory.Tools entryName = allEntries[i].name;
            if (null != currentList && !currentList.contains(entryName.name())) {
                continue;
            }
            entryMap.put(entryName.name(), allEntries[i]);
        }

        if (null != currentList) {
            for (String toolName : currentList) {
                if (!entryMap.containsKey(toolName)) {
                    continue;
                }
                listEntries.add(entryMap.get(toolName));
            }
        }

        return new Pair<List<String>, List<ToolEntry>>(currentList, listEntries);
    }
}
