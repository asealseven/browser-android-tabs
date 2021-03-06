// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef CHROME_BROWSER_WEB_APPLICATIONS_EXTENSIONS_WEB_APP_EXTENSION_SHORTCUT_MAC_H_
#define CHROME_BROWSER_WEB_APPLICATIONS_EXTENSIONS_WEB_APP_EXTENSION_SHORTCUT_MAC_H_

#include <string>
#include <vector>

#include "base/callback_forward.h"
#include "base/files/file_path.h"

class Profile;

namespace base {
class CommandLine;
}

namespace extensions {
class Extension;
}

namespace web_app {

// Rebuild the shortcut and relaunch it.
bool MaybeRebuildShortcut(const base::CommandLine& command_line);

// Reveals app shim in Finder given a profile and app.
// Calls RevealAppShimInFinderForAppOnFileThread and schedules it
// on the FILE thread.
void RevealAppShimInFinderForApp(Profile* profile,
                                 const extensions::Extension* app);

}  // namespace web_app

#endif  // CHROME_BROWSER_WEB_APPLICATIONS_EXTENSIONS_WEB_APP_EXTENSION_SHORTCUT_MAC_H_
