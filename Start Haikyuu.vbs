Option Explicit

Dim shell, files, folder, appFolder, exePath, result
Set shell = CreateObject("WScript.Shell")
Set files = CreateObject("Scripting.FileSystemObject")
folder = files.GetParentFolderName(WScript.ScriptFullName)
appFolder = files.BuildPath(folder, "release\HaiKyuu")
exePath = files.BuildPath(appFolder, "HaiKyuu.exe")

If Not files.FileExists(exePath) Or Not files.FileExists(files.BuildPath(appFolder, "app\HaiKyuu.jar")) Or Not files.FolderExists(files.BuildPath(appFolder, "runtime")) Then
    MsgBox "HaiKyuu release is missing. Run build-release.bat first.", vbExclamation, "HaiKyuu"
    WScript.Quit 1
End If

shell.CurrentDirectory = folder
On Error Resume Next
result = shell.Run(Chr(34) & exePath & Chr(34), 1, True)
If Err.Number <> 0 Then
    MsgBox "HaiKyuu could not start: " & Err.Description, vbCritical, "HaiKyuu"
    WScript.Quit 1
End If
On Error GoTo 0
If result <> 0 Then
    MsgBox "HaiKyuu exited with code " & result & ".", vbCritical, "HaiKyuu"
End If
