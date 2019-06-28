;This file will be executed next to the application bundle image
;I.e. current directory will contain folder MIBViewer with application files
[Setup]
AppId={{fxApplication}}
AppName=MIBViewer
AppVersion=1.0
AppVerName=MIBViewer 1.0
AppPublisher=3IC
AppComments=MIBViewer
AppCopyright=Copyright (C) 2016
;AppPublisherURL=http://java.com/
;AppSupportURL=http://java.com/
;AppUpdatesURL=http://java.com/
;DefaultDirName={localappdata}\MIBViewer
DefaultDirName=c:\MIBViewer
DisableStartupPrompt=Yes
;DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=3IC
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1 
OutputBaseFilename=MIBViewer-1.0
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=MIBViewer\MIBViewer.ico
UninstallDisplayIcon={app}\MIBViewer.ico
UninstallDisplayName=MIBViewer
WizardImageStretch=No
WizardSmallImageFile=MIBViewer-setup-icon.bmp   
;WizardImageFile=MIBViewer-install-image.bmp 
ArchitecturesInstallIn64BitMode=

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "MIBViewer\MIBViewer.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "MIBViewer\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\MIBViewer"; Filename: "{app}\MIBViewer.exe"; IconFilename: "{app}\MIBViewer.ico"; Check: returnTrue()
Name: "{commondesktop}\MIBViewer"; Filename: "{app}\MIBViewer.exe";  IconFilename: "{app}\MIBViewer.ico"; Check: returnFalse()


[Run]
Filename: "{app}\MIBViewer.exe"; Parameters: "-Xappcds:generatecache"; Check: returnFalse()
Filename: "{app}\MIBViewer.exe"; Description: "{cm:LaunchProgram,MIBViewer}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\MIBViewer.exe"; Parameters: "-install -svcName ""MIBViewer"" -svcDesc ""MIBViewer"" -mainExe ""MIBViewer.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\MIBViewer.exe "; Parameters: "-uninstall -svcName MIBViewer -stopOnUninstall"; Check: returnFalse()

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support? 
  Result := True;
end;  
