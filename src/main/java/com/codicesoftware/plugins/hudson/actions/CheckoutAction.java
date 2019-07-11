package com.codicesoftware.plugins.hudson.actions;

import com.codicesoftware.plugins.hudson.PlasticTool;
import com.codicesoftware.plugins.hudson.model.Workspace;
import hudson.FilePath;
import org.apache.commons.io.FilenameUtils;

import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CheckoutAction {

    private static final Logger logger = Logger.getLogger(CheckoutAction.class.getName());

    public static Workspace checkout(
            PlasticTool tool,
            FilePath workspacePath,
            String selector,
            boolean useUpdate)
            throws IOException, InterruptedException, ParseException {
        List<Workspace> workspaces = Workspaces.loadWorkspaces(tool);

        cleanOldWorkspacesIfNeeded(tool, workspacePath, useUpdate, workspaces);

        if (!useUpdate && workspacePath.exists()) {
            workspacePath.deleteContents();
        }

        return checkoutWorkspace(tool, workspacePath, selector, workspaces);
    }

    private static Workspace checkoutWorkspace(
            PlasticTool tool,
            FilePath workspacePath,
            String selector,
            List<Workspace> workspaces) throws IOException, InterruptedException, ParseException {

        Workspace workspace = findWorkspaceByPath(workspaces, workspacePath);

        if (workspace != null) {
            logger.fine("Reusing existing workspace");
            Workspaces.cleanWorkspace(tool, workspace.getPath());

            if (mustUpdateSelector(tool, workspace.getName(), selector)) {
                Workspaces.setWorkspaceSelector(tool, workspacePath, selector);
                return workspace;
            } else {
                Workspaces.updateWorkspace(tool, workspace.getPath());
            }
        } else {
            logger.fine("Creating new workspace");
            String uniqueWorkspaceName = Workspaces.generateUniqueWorkspaceName();

            workspace = Workspaces.newWorkspace(tool, workspacePath, uniqueWorkspaceName, selector);

            Workspaces.cleanWorkspace(tool, workspace.getPath());
            Workspaces.updateWorkspace(tool, workspace.getPath());
        }

        return workspace;
    }

    private static boolean mustUpdateSelector(PlasticTool tool, String name, String selector) {
        String wkSelector = removeNewLinesFromSelector(
            Workspaces.loadSelector(tool, name));
        String currentSelector = removeNewLinesFromSelector(selector);

        return !wkSelector.equals(currentSelector);
    }

    private static String removeNewLinesFromSelector(String selector) {
        return selector.trim().replace("\r\n", "").replace("\n", "").replace("\r", "");
    }

    private static void cleanOldWorkspacesIfNeeded(
            PlasticTool tool,
            FilePath workspacePath,
            boolean shouldUseUpdate,
            List<Workspace> workspaces) throws IOException, InterruptedException {

        // handle situation where workspace exists in parent path
        Workspace parentWorkspace = findWorkspaceByPath(workspaces, workspacePath.getParent());
        if (parentWorkspace != null) {
            deleteWorkspace(tool, parentWorkspace, workspaces);
        }

        // handle situation where workspace exists in child path
        List<Workspace> nestedWorkspaces = findWorkspacesInsidePath(workspaces, workspacePath);
        for (Workspace workspace : nestedWorkspaces) {
            deleteWorkspace(tool, workspace, workspaces);
        }

        if (shouldUseUpdate) {
            return;
        }

        Workspace workspace = findWorkspaceByPath(workspaces, workspacePath);
        if (workspace != null) {
            deleteWorkspace(tool, workspace, workspaces);
        }
    }

    private static boolean isSamePath(String expected, String actual) {
        Matcher windowsPathMatcher = windowsPathPattern.matcher(actual);
        if (windowsPathMatcher.matches()) {
            return actual.equalsIgnoreCase(expected);
        }
        return actual.equals(expected);
    }

    private static void deleteWorkspace(
            PlasticTool tool, Workspace workspace, List<Workspace> workspaces)
            throws IOException, InterruptedException {
        Workspaces.deleteWorkspace(tool, workspace.getPath());
        workspace.getPath().deleteContents();
        workspaces.remove(workspace);
    }

    @Deprecated
    private static Workspace findWorkspaceByName(List<Workspace> workspaces, String workspaceName)
    {
        for (Workspace workspace : workspaces) {
            if(workspace.getName().equals(workspaceName))
                return workspace;
        }
        return null;
    }

    private static Workspace findWorkspaceByPath(List<Workspace> workspaces, FilePath workspacePath)
    {
        for (Workspace workspace : workspaces) {
            if (isSamePath(workspace.getPath().getRemote(), workspacePath.getRemote()))
                return workspace;
        }
        return null;
    }

    private static List<Workspace> findWorkspacesInsidePath(List<Workspace> workspaces, FilePath workspacePath)
    {
        List<Workspace> result = new ArrayList<>();

        for (Workspace workspace : workspaces) {
            String parentPath = FilenameUtils.getFullPathNoEndSeparator(workspace.getPath().getRemote());
            if (isSamePath(parentPath, workspacePath.getRemote()))
                result.add(workspace);
        }
        return result;
    }

    private static Pattern windowsPathPattern = Pattern.compile("^[a-zA-Z]:\\\\.*$");
}
