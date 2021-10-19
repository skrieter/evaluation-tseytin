/* -----------------------------------------------------------------------------
 * Evaluation-Tseytin - Program for the evaluation of the Tseytin transformation.
 * Copyright (C) 2021  Sebastian Krieter, Elias Kuiter
 *
 * This file is part of Evaluation-Tseytin.
 *
 * Evaluation-Tseytin is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * Evaluation-Tseytin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-Tseytin.  If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/ekuiter/evaluation-tseytin> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.tseytin;

import de.ovgu.featureide.fm.core.base.impl.FMFormatManager;
import de.ovgu.featureide.fm.core.init.FMCoreLibrary;
import de.ovgu.featureide.fm.core.init.LibraryManager;
import org.spldev.evaluation.tseytin.analysis.Analysis;
import org.spldev.util.extension.ExtensionLoader;

import java.nio.file.Paths;
import java.util.Objects;

public class Runner {
	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new RuntimeException("invalid usage");
		}
		ExtensionLoader.load();
		LibraryManager.registerLibrary(FMCoreLibrary.getInstance());
		FMFormatManager.getInstance().addExtension(new KConfigReaderFormat());
		Analysis analysis = Analysis.read(Paths.get(args[0]));
		Objects.requireNonNull(analysis);
		System.out.println(analysis);
		analysis.run();
	}
}
