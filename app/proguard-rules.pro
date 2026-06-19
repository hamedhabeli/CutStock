-keep class com.example.cutstock.nativecore.CuttingPlan { *; }
-keep class com.example.cutstock.nativecore.Bin { *; }
-keep class com.example.cutstock.nativecore.NativeSolver { *; }
-keep class com.example.cutstock.data.ProjectEntity { *; }
-keep class com.example.cutstock.data.DemandEntity { *; }
-keep class com.example.cutstock.data.ProjectWithDemands { *; }
-keep class com.example.cutstock.data.CuttingPlanConverters { *; }
-keep class com.example.cutstock.data.IntListConverters { *; }
-keep class com.example.cutstock.presentation.ProjectUiState { *; }
-keep class com.example.cutstock.domain.SalesSummary { *; }
-keep class com.example.cutstock.presentation.ProjectViewModel { *; }
-keep class com.example.cutstock.presentation.MainActivity { *; }
-keep class com.example.cutstock.presentation.ProjectListActivity { *; }
-keepattributes *Annotation*
-dontwarn com.google.gson.**

# Prevent R8/Proguard from stripping Apache POI and openxmlformats classes
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.etsi.uri.x2001.x03.xmldsig.** { *; }
-keep class com.microsoft.schemas.** { *; }

# Ignore warnings about missing classes that are not available or used on Android
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.uri.**
-dontwarn com.microsoft.schemas.**
-dontwarn javax.xml.stream.**
-dontwarn javax.xml.namespace.**
-dontwarn java.awt.**
-dontwarn org.w3c.dom.**
-dontwarn org.xml.sax.**

# Ignore warnings for missing optional dependencies of POI/XMLBeans (e.g. XMLBeans, Saxon, OSGi, Log4j)
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.framework.**
-dontwarn org.apache.logging.log4j.**
