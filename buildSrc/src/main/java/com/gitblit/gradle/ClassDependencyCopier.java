/*
 * Copyright 2019 Tue Ton
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gitblit.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds all dependency classes of a given set of input classes
 * and copies them to a destination directory.
 * Dependency classes are filtered to exclude 3rd-party dependencies.
 */
public class ClassDependencyCopier extends DefaultTask {

	private static Logger logger = LoggerFactory.getLogger(ClassDependencyCopier.class.getName());

	List<String> classNames;
	List<String> filteredPackages;
	File classesDir;
	File outputDir;
	
	@Input
	public List<String> getClassNames() {
		return classNames;
	}

	@Input
	public List<String> getFilteredPackages() {
		return filteredPackages;
	}

	@InputDirectory
	public File getClassesDir() {
		return classesDir;
	}

	@OutputDirectory
	public File getOutputDir() {
		return outputDir;
	}

	public void setClassNames(List<String> classNames) {
		this.classNames = classNames;
	}

	public void setFilteredPackages(List<String> filteredPackages) {
		this.filteredPackages = filteredPackages;
	}

	public void setClassesDir(File classesDir) {
		this.classesDir = classesDir;
	}

	public void setOutputDir(File outputDir) {
		this.outputDir = outputDir;
	}

	@TaskAction
	public void copyDependencyClasses() throws IOException {
        filteredPackages = filteredPackages.stream().map(pkg -> pkg.replace(".", "/"))
        											.collect(Collectors.toList());
		Set<String> dependencyClasses = classNames.stream().map(clazz -> clazz.replace(".", "/"))
														   .collect(Collectors.toCollection(HashSet::new));

		List<String> deps = new ArrayList<String>(dependencyClasses);
		while (!deps.isEmpty()) {
			DependencyVisitor visitor = new DependencyVisitor();
			for (String clazz : deps) {
				try (FileInputStream fis = new FileInputStream(new File(classesDir, clazz + ".class"))) {
					new ClassReader(fis).accept(visitor, 0);
				}
			}
			deps = filterDependencySet(visitor.getDependencyClasses(), dependencyClasses);
			dependencyClasses.addAll(deps);
		}
		logger.info("Class dependencies: " + dependencyClasses.size());

		for (String clazz : dependencyClasses) {
			File inClass = new File(classesDir, clazz + ".class");
			File outClass = new File(outputDir, clazz + ".class");
			outClass.getParentFile().mkdirs();
			Files.copy(inClass.toPath(), outClass.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
	}

	private List<String> filterDependencySet(Set<String> dependencies, Set<String> existingDependencies) {
		List<String> filtered = new ArrayList<String>();
		for (String clazz : dependencies) {
			for (String pkg : filteredPackages) {
				if (clazz.startsWith(pkg)) {
					if (!existingDependencies.contains(clazz)) {
						filtered.add(clazz);
					}
					break;
				}
			}
		}
		return filtered;
	}

}
