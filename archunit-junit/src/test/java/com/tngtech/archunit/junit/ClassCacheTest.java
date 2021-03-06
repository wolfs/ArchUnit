package com.tngtech.archunit.junit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ImportOptions;
import com.tngtech.archunit.core.importer.Location;
import com.tngtech.archunit.junit.ClassCache.CacheClassFileImporter;
import com.tngtech.archunit.testutil.ArchConfigurationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@SuppressWarnings("unchecked")
public class ClassCacheTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Rule
    public final ArchConfigurationRule archConfigurationRule = new ArchConfigurationRule()
            .resolveAdditionalDependenciesFromClassPath(false);

    @Spy
    private CacheClassFileImporter cacheClassFileImporter;

    @InjectMocks
    private ClassCache cache = new ClassCache();

    @Test
    public void loads_classes() {
        JavaClasses classes = cache.getClassesToAnalyzeFor(TestClass.class);

        assertThat(classes).as("Classes were found").isNotEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reuses_loaded_classes_by_test() {
        cache.getClassesToAnalyzeFor(TestClass.class);
        cache.getClassesToAnalyzeFor(TestClass.class);

        verifyNumberOfImports(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reuses_loaded_classes_by_urls() {
        cache.getClassesToAnalyzeFor(TestClass.class);
        cache.getClassesToAnalyzeFor(EquivalentTestClass.class);

        verifyNumberOfImports(1);
    }

    @Test
    public void rejects_missing_analyze_annotation() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage(Object.class.getSimpleName());
        thrown.expectMessage("must be annotated");
        thrown.expectMessage(AnalyzeClasses.class.getSimpleName());

        cache.getClassesToAnalyzeFor(Object.class);
    }

    @Test
    public void filters_jars_relative_to_class() {
        JavaClasses classes = cache.getClassesToAnalyzeFor(TestClassWithFilterJustByPackageOfClass.class);

        assertThat(classes).isNotEmpty();
        for (JavaClass clazz : classes) {
            assertThat(clazz.getPackage()).doesNotContain("tngtech");
        }
    }

    @Test
    public void gets_all_classes_relative_to_class() {
        JavaClasses classes = cache.getClassesToAnalyzeFor(TestClassWithFilterJustByPackageOfClass.class);

        assertThat(classes).isNotEmpty();
    }

    @Test
    public void filters_urls() {
        JavaClasses classes = cache.getClassesToAnalyzeFor(TestClassFilteringJustJUnitJars.class);

        assertThat(classes).isNotEmpty();
        for (JavaClass clazz : classes) {
            assertThat(clazz.getPackage()).doesNotContain(ClassCache.class.getSimpleName());
            assertThat(clazz.getPackage()).contains("junit");
        }
    }

    @Test
    public void non_existing_packages_are_ignored() {
        JavaClasses first = cache.getClassesToAnalyzeFor(TestClassWithNonExistingPackage.class);
        JavaClasses second = cache.getClassesToAnalyzeFor(TestClassWithFilterJustByPackageOfClass.class);

        assertThat(first).isEqualTo(second);
        verifyNumberOfImports(1);
    }

    @Test
    public void distinguishes_import_option_when_caching() {
        JavaClasses importingWholeClasspathWithFilter =
                cache.getClassesToAnalyzeFor(TestClassFilteringJustJUnitJars.class);
        JavaClasses importingWholeClasspathWithEquivalentButDifferentFilter =
                cache.getClassesToAnalyzeFor(TestClassFilteringJustJUnitJarsWithDifferentFilter.class);

        assertThat(importingWholeClasspathWithFilter)
                .as("number of classes imported")
                .hasSameSizeAs(importingWholeClasspathWithEquivalentButDifferentFilter);

        verifyNumberOfImports(2);
    }

    private void verifyNumberOfImports(int number) {
        verify(cacheClassFileImporter, times(number)).importClasses(any(ImportOptions.class), anyCollection());
        verifyNoMoreInteractions(cacheClassFileImporter);
    }

    @AnalyzeClasses(packages = "com.tngtech.archunit.junit")
    public static class TestClass {
    }

    @AnalyzeClasses(packages = "com.tngtech.archunit.junit")
    public static class EquivalentTestClass {
    }

    @AnalyzeClasses(packagesOf = Rule.class)
    public static class TestClassWithFilterJustByPackageOfClass {
    }

    @AnalyzeClasses(packages = "something.that.doesnt.exist", packagesOf = Rule.class)
    public static class TestClassWithNonExistingPackage {
    }

    @AnalyzeClasses(importOptions = TestFilterForJUnitJars.class)
    public static class TestClassFilteringJustJUnitJars {
    }

    @AnalyzeClasses(importOptions = AnotherTestFilterForJUnitJars.class)
    public static class TestClassFilteringJustJUnitJarsWithDifferentFilter {
    }

    public static class TestFilterForJUnitJars implements ImportOption {
        @Override
        public boolean includes(Location location) {
            return location.contains("junit") && location.contains(".jar");
        }
    }

    public static class AnotherTestFilterForJUnitJars implements ImportOption {
        private TestFilterForJUnitJars filter = new TestFilterForJUnitJars();

        @Override
        public boolean includes(Location location) {
            return filter.includes(location);
        }
    }
}