DATA KAAVAL - ANDROID APP (APK) VAANGUVADHU EPPADI

Ungalukku thevai: oru computer, free GitHub account (github.com).

STEP 1: GitHub-la pudhu repository
- github.com -> "+" -> New repository
- Name: data-kaaval, Public, "Create repository"

STEP 2: Files upload
- "uploading an existing file" link-ai thottunga
- Indha zip-ai unzip pannina folder-kulla irukkura ELLA files, folders-ayum drag panni podunga
  (app folder, build.gradle, settings.gradle, gradle.properties)
- "Commit changes"

STEP 3: Build file (hidden folder upload aagaama pona mattum)
- Repository-la "Add file" -> "Create new file"
- File name-la type pannunga:  .github/workflows/build.yml
- .github-ukku ulla irukkura build.yml file-in ullae irukkura text-ai appadiye paste pannunga
- "Commit changes"

STEP 4: APK varum varai kaathirunga (3-6 nimidam)
- "Actions" tab -> "Build APK" -> pachai tick vandhaal mudinjadhu
- Andha run-ai thottu, keezha "Artifacts" -> "DataKaaval-apk" -> download
- Zip-ai unzip pannina "app-debug.apk" kidaikkum

STEP 5: Phone-la install
- app-debug.apk-ai phone-ku anuppunga, thottunga
- "Install unknown apps" kettaal Allow pannunga
- App open pannunga -> "Open settings" -> "Data Kaaval" -> Allow usage access
- Back vandhu, "Limits" tab-la plan, app limits set pannunga
- "Limits" tab -> Alerts-la "Phone notification" on pannina permission kekkum, Allow pannunga

Build fail aanaal: Actions-la red X-ai thottu, error text-ai enakku anuppunga.
