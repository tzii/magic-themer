# QA icon pack fixture

This local test application contains original vector artwork and a 768px raster icon. It has no permissions or network dependencies and is not part of the distributable Magic Themer app.

Its compiled `res/xml/appfilter.xml` contains five mappings: Magic Themer, Android Settings, this fixture (using a short activity name), Play Store with a deliberately absent drawable, and a future app that is not installed. Its `drawable.xml` includes unmapped alternatives, including a Play Store replacement for the suggestion test.

Build `:qa:iconpack:assembleDebug` and install its debug APK alongside Magic Themer. Whole-pack export produces four icons, including the absent app, and reports one missing drawable. The visual gallery exposes six usable artworks and excludes the missing resource. Play Store appears under Needs icon; its alternative is suggested and can be assigned with one tap. The raster icon tests downsampling to a 192px export. The fixture does not prove that HONOR Themes accepts the generated archive; that requires testing on the target Magic V5.
