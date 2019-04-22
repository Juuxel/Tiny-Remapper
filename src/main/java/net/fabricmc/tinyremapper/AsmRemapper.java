/*
 * Copyright (C) 2016, 2018 Player, asie
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.fabricmc.tinyremapper;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Queue;
import java.util.Set;

import org.objectweb.asm.commons.Remapper;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;

class AsmRemapper extends Remapper {
	public AsmRemapper(TinyRemapper remapper) {
		this.remapper = remapper;
	}

	@Override
	public String map(String typeName) {
		String ret = remapper.classMap.get(typeName);

		return ret != null ? ret : typeName;
	}

	@Override
	public String mapFieldName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolve(MemberType.FIELD, MemberInstance.getFieldId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.fieldMap.get(owner+"/"+MemberInstance.getFieldId(name, desc))) == null || newName.equals(name);

		return name;
	}

	@Override
	public String mapMethodName(String owner, String name, String desc) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		assert (newName = remapper.methodMap.get(owner+"/"+MemberInstance.getMethodId(name, desc))) == null || newName.equals(name);

		return name;
	}

	public String mapMethodNamePrefixDesc(String owner, String name, String descPrefix) {
		ClassInstance cls = getClass(owner);
		if (cls == null) return name;

		MemberInstance member = cls.resolvePartial(MemberType.METHOD, name,descPrefix);
		String newName;

		if (member != null && (newName = member.getNewName()) != null) {
			return newName;
		}

		return name;
	}

	public String mapLambdaInvokeDynamicMethodName(String owner, String name, String desc) {
		return mapMethodName(owner, name, desc);
	}

	public String mapArbitraryInvokeDynamicMethodName(String owner, String name) {
		return mapMethodNamePrefixDesc(owner, name, null);
	}

	public String[] getLocalVariables(String owner, String name, String desc) {
		String[] locals = remapper.localMap.get(mapType(owner) + '/' + name + desc);
		if (locals != null) return locals;

		ClassInstance cls = getClass(owner);
		if (cls == null) return new String[0];

		MemberInstance method = cls.resolve(MemberType.METHOD, MemberInstance.getMethodId(name, desc));
		if (method != null) {
			locals = remapper.localMap.get(map(method.cls.getName()) + '/' + method.name + method.desc);
			if (locals != null) return locals;
		}

		Set<ClassInstance> tried = Collections.newSetFromMap(new IdentityHashMap<>());
		tried.add(cls);

		Queue<ClassInstance> possibilities = new ArrayDeque<>(cls.parents);
		while (!possibilities.isEmpty()) {
			ClassInstance possibility = possibilities.poll();

			locals = remapper.localMap.get(map(possibility.getName()) + '/' + name + desc);
			if (locals != null) return locals;

			tried.add(possibility);
			for (ClassInstance parent : possibility.parents) {
				if (!tried.contains(parent)) possibilities.add(parent);
			}
		}

		return new String[0];
	}
	
	private ClassInstance getClass(String owner) {
		return remapper.classes.get(owner);
	}

	private final TinyRemapper remapper;
}
