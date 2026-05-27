package com.wxalh.airan_desk.terminal;

import java.util.Locale;

public final class TerminalPathTrackingScript {
    private static final String POSIX_SCRIPT = "export AIRAN_OLD_PROMPT_COMMAND=\"$PROMPT_COMMAND\"\nexport PROMPT_COMMAND='printf \"\\033]7;file://localhost%s\\007\" \"$PWD\"; if [ -n \"$AIRAN_OLD_PROMPT_COMMAND\" ]; then eval \"$AIRAN_OLD_PROMPT_COMMAND\"; fi'\n";
    private static final String POWERSHELL_SCRIPT = "function global:prompt { $p=(Get-Location).Path; $u='file:///'+$p.Replace('\\\\','/').Replace(' ','%20'); [Console]::Write(\"`e]7;$u`a\"); \"PS $p> \" }\r";
    private static final String CMD_SCRIPT = "prompt $E]7;file:///$P$E\\$G$S$P$G\r";

    private TerminalPathTrackingScript() {
    }

    public static String forShell(String os, String shell) {
        String normalizedOs = os == null ? "" : os.toLowerCase(Locale.US);
        String normalizedShell = shell == null ? "" : shell.toLowerCase(Locale.US);
        if (normalizedOs.contains("windows")) {
            return normalizedShell.contains("powershell") || normalizedShell.contains("pwsh") ? POWERSHELL_SCRIPT : CMD_SCRIPT;
        }
        return POSIX_SCRIPT;
    }
}
