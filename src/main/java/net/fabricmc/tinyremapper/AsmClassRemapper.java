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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.lang.model.SourceVersion;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.commons.AnnotationRemapper;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.FieldRemapper;
import org.objectweb.asm.commons.MethodRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

import net.fabricmc.tinyremapper.MemberInstance.MemberType;

class AsmClassRemapper extends ClassRemapper {
	public AsmClassRemapper(ClassVisitor cv, AsmRemapper remapper, boolean checkPackageAccess, boolean skipLocalMapping, boolean renameInvalidLocals) {
		super(cv, remapper);

		this.checkPackageAccess = checkPackageAccess;
		this.skipLocalMapping = skipLocalMapping;
		this.renameInvalidLocals = renameInvalidLocals;
	}

	@Override
	public void visitSource(String source, String debug) {
		String mappedClsName = remapper.map(className);
		// strip package
		int start = mappedClsName.lastIndexOf('/') + 1;
		// strip inner classes
		int end = mappedClsName.indexOf('$');
		if (end <= 0) end = mappedClsName.length(); // require at least 1 character for the outer class

		super.visitSource(mappedClsName.substring(start, end).concat(".java"), debug);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
		if (!skipLocalMapping || renameInvalidLocals) {
			methodNode = new MethodNode(api, access, name, descriptor, signature, exceptions);
		}

		return super.visitMethod(access, name, descriptor, signature, exceptions);
	}

	@Override
	protected FieldVisitor createFieldRemapper(FieldVisitor fieldVisitor) {
		return new AsmFieldRemapper(fieldVisitor, remapper);
	}

	@Override
	protected MethodVisitor createMethodRemapper(MethodVisitor mv) {
		return new AsmMethodRemapper(mv, remapper, className, methodNode, checkPackageAccess, skipLocalMapping, renameInvalidLocals);
	}

	@Override
	public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
		return createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
	}

	@Override
	public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
		return createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
	}

	public static AnnotationRemapper createAsmAnnotationRemapper(String desc, AnnotationVisitor annotationVisitor, Remapper remapper) {
		return annotationVisitor == null ? null : new AsmAnnotationRemapper(annotationVisitor, remapper, desc);
	}

	private final boolean checkPackageAccess;
	private final boolean skipLocalMapping;
	private final boolean renameInvalidLocals;
	private MethodNode methodNode;

	private static class AsmFieldRemapper extends FieldRemapper {
		public AsmFieldRemapper(FieldVisitor fieldVisitor, Remapper remapper) {
			super(fieldVisitor, remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
		}
	}

	private static class AsmMethodRemapper extends MethodRemapper {
		public AsmMethodRemapper(MethodVisitor methodVisitor, Remapper remapper, String owner, MethodNode methodNode, boolean checkPackageAccess, boolean skipLocalMapping, boolean renameInvalidLocals) {
			super(methodNode != null ? methodNode : methodVisitor, remapper);

			this.owner = owner;
			this.methodNode = methodNode;
			this.output = methodVisitor;
			this.checkPackageAccess = checkPackageAccess;
			this.skipLocalMapping = skipLocalMapping;
			this.renameInvalidLocals = renameInvalidLocals;
		}

		@Override
		public AnnotationVisitor visitAnnotationDefault() {
			return AsmClassRemapper.createAsmAnnotationRemapper(Type.getObjectType(owner).getDescriptor(), super.visitAnnotationDefault(), remapper);
		}

		@Override
		public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitAnnotation(descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitTypeAnnotation(typeRef, typePath, descriptor, visible), remapper);
		}

		@Override
		public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, super.visitParameterAnnotation(parameter, descriptor, visible), remapper);
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			if (checkPackageAccess) {
				((AsmRemapper) remapper).checkPackageAccess(this.owner, owner, name, descriptor, MemberType.FIELD);
			}

			super.visitFieldInsn(opcode, owner, name, descriptor);
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			if (checkPackageAccess) {
				((AsmRemapper) remapper).checkPackageAccess(this.owner, owner, name, descriptor, MemberType.METHOD);
			}

			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			Handle implemented = getLambdaImplementedMethod(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

			if (implemented != null) {
				name = remapper.mapMethodName(implemented.getOwner(), implemented.getName(), implemented.getDesc());
			} else {
				name = remapper.mapInvokeDynamicMethodName(name, descriptor);
			}

			for (int i = 0; i < bootstrapMethodArguments.length; i++) {
				bootstrapMethodArguments[i] = remapper.mapValue(bootstrapMethodArguments[i]);
			}

			mv.visitInvokeDynamicInsn( // bypass remapper
					name,
					remapper.mapMethodDesc(descriptor), (Handle) remapper.mapValue(bootstrapMethodHandle),
					bootstrapMethodArguments);
		}

		private static Handle getLambdaImplementedMethod(String name, String desc, Handle bsm, Object... bsmArgs) {
			if (isJavaLambdaMetafactory(bsm)) {
				assert desc.endsWith(";");
				return new Handle(Opcodes.H_INVOKEINTERFACE, desc.substring(desc.lastIndexOf(')') + 2, desc.length() - 1), name, ((Type) bsmArgs[0]).getDescriptor(), true);
			} else {
				System.out.printf("unknown invokedynamic bsm: %s/%s%s (tag=%d iif=%b)%n", bsm.getOwner(), bsm.getName(), bsm.getDesc(), bsm.getTag(), bsm.isInterface());

				return null;
			}
		}

		private static boolean isJavaLambdaMetafactory(Handle bsm) {
			return bsm.getTag() == Opcodes.H_INVOKESTATIC
					&& bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
					&& (bsm.getName().equals("metafactory")
							&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;")
							|| bsm.getName().equals("altMetafactory")
							&& bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;"))
					&& !bsm.isInterface();
		}

		@Override
		public void visitEnd() {
			if (methodNode != null) {
				if (!skipLocalMapping
						|| renameInvalidLocals && (methodNode.localVariables != null && !methodNode.localVariables.isEmpty() || methodNode.parameters != null && !methodNode.parameters.isEmpty())) {
					processLocals();
				}

				methodNode.visitEnd();
				methodNode.accept(output);
			} else {
				super.visitEnd();
			}
		}

		private void processLocals() {
			final boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
			final Type[] argTypes = Type.getArgumentTypes(methodNode.desc);
			final int argLvSize = getLvIndex(argTypes.length, isStatic, argTypes);
			final String[] args = new String[argTypes.length];

			// grab arg names from parameters
			if (methodNode.parameters != null && methodNode.parameters.size() == args.length) {
				for (int i = 0; i < args.length; i++) {
					args[i] = methodNode.parameters.get(i).name;
				}
			} else {
				assert methodNode.parameters == null;
			}

			// grab arg names from lvs, fix "this", remap vars
			if (methodNode.localVariables != null) {
				for (int i = 0; i < methodNode.localVariables.size(); i++) {
					LocalVariableNode lv = methodNode.localVariables.get(i);

					if (!isStatic && lv.index == 0) { // this ref
						lv.name = "this";
					} else if (lv.index < argLvSize) { // arg
						int asmIndex = getAsmIndex(lv.index, isStatic, argTypes);
						String existingName = args[asmIndex];

						if (existingName == null || !isValidJavaIdentifier(existingName) && isValidJavaIdentifier(lv.name)) { // replace if missing or better
							args[asmIndex] = lv.name;
						}

						// remap+fix later
					} else { // var
						if (!skipLocalMapping) {
							int startOpIdx = 0;
							AbstractInsnNode start = lv.start;

							while ((start = start.getPrevious()) != null) {
								if (start.getOpcode() >= 0) startOpIdx++;
							}

							lv.name = ((AsmRemapper) remapper).mapMethodVar(owner, methodNode.name, methodNode.desc, lv.index, startOpIdx, i, lv.name);

							if (renameInvalidLocals && isValidJavaIdentifier(lv.name)) { // block valid name from generation
								nameCounts.putIfAbsent(lv.name, 1);
							}
						}

						// fix later
					}
				}
			}

			// remap args
			if (!skipLocalMapping) {
				for (int i = 0; i < args.length; i++) {
					args[i] = ((AsmRemapper) remapper).mapMethodArg(owner, methodNode.name, methodNode.desc, getLvIndex(i, isStatic, argTypes), args[i]);

					if (renameInvalidLocals && isValidJavaIdentifier(args[i])) { // block valid name from generation
						nameCounts.putIfAbsent(args[i], 1);
					}
				}
			}

			// fix args
			if (renameInvalidLocals) {
				for (int i = 0; i < args.length; i++) {
					if (!isValidJavaIdentifier(args[i])) {
						args[i] = getNameFromType(remapper.mapDesc(argTypes[i].getDescriptor()), true);
					}
				}
			}

			boolean hasAnyArgs = false;

			for (String arg : args) {
				if (arg != null) {
					hasAnyArgs = true;
					break;
				}
			}

			// update lvs, fix vars
			if (methodNode.localVariables != null
					|| methodNode.parameters == null && (methodNode.access & Opcodes.ACC_ABSTRACT) == 0 && hasAnyArgs) { // avoid creating parameters if possible (non-abstract), create lvs instead
				boolean[] argsWritten = new boolean[args.length];

				if (methodNode.localVariables == null) {
					methodNode.localVariables = new ArrayList<>();
				} else {
					for (LocalVariableNode lv : methodNode.localVariables) {
						if (!isStatic && lv.index == 0) { // this ref
							// nothing
						} else if (lv.index < argLvSize) { // arg
							int asmIndex = getAsmIndex(lv.index, isStatic, argTypes);
							lv.name = args[asmIndex];
							argsWritten[asmIndex] = true;
						} else { // var
							if (renameInvalidLocals && !isValidJavaIdentifier(lv.name)) {
								lv.name = getNameFromType(lv.desc, false);
							}
						}
					}
				}

				LabelNode start = null;
				LabelNode end = null;

				for (int i = 0; i < args.length; i++) {
					if (!argsWritten[i] && args[i] != null) {
						if (start == null) { // lazy initialize start + end by finding the first and last label node
							for (Iterator<AbstractInsnNode> it = methodNode.instructions.iterator(); it.hasNext(); ) {
								AbstractInsnNode ain = it.next();

								if (ain.getType() == AbstractInsnNode.LABEL) {
									LabelNode label = (LabelNode) ain;
									if (start == null) start = label;
									end = label;
								}
							}

							if (start == null) { // no labels -> can't create lvs
								assert false;
								break;
							}
						}

						methodNode.localVariables.add(new LocalVariableNode(args[i], remapper.mapDesc(argTypes[i].getDescriptor()), null, start, end, getLvIndex(i, isStatic, argTypes)));
					}
				}
			}

			// update parameters
			if (methodNode.parameters != null
					|| (methodNode.access & Opcodes.ACC_ABSTRACT) != 0 && hasAnyArgs) {
				if (methodNode.parameters == null) {
					methodNode.parameters = new ArrayList<>(args.length);
				}

				while (methodNode.parameters.size() < args.length) {
					methodNode.parameters.add(new ParameterNode(null, 0));
				}

				for (int i = 0; i < args.length; i++) {
					methodNode.parameters.get(i).name = args[i];
				}
			}
		}

		private static int getLvIndex(int asmIndex, boolean isStatic, Type[] argTypes) {
			int ret = 0;

			if (!isStatic) ret++;

			for (int i = 0; i < asmIndex; i++) {
				ret += argTypes[i].getSize();
			}

			return ret;
		}

		private static int getAsmIndex(int lvIndex, boolean isStatic, Type[] argTypes) {
			if (!isStatic) lvIndex--;

			for (int i = 0; i < argTypes.length; i++) {
				if (lvIndex == 0) return i;
				lvIndex -= argTypes[i].getSize();
			}

			return -1;
		}

		private String getNameFromType(String type, boolean isArg) {
			boolean plural = false;

			if (type.charAt(0) == '[') {
				plural = true;
				type = type.substring(type.lastIndexOf('[') + 1);
			}

			String varName;
			switch (type.charAt(0)) {
			case 'B': varName = "b"; break;
			case 'C': varName = "c"; break;
			case 'D': varName = "d"; break;
			case 'F': varName = "f"; break;
			case 'I': {
				//Strictly speaking is shouldn't ever fail the identifier check, but this covers any future revelations
				varName = plural && isValidJavaIdentifier("is") ? "is" : "i";

				int index = 1;
				while (nameCounts.putIfAbsent(varName, 0) != null) {
					switch (varName.charAt(0)) {
					case 'i':
						varName = 'j' + varName.substring(1);
						break;

					case 'j':
						varName = 'k' + varName.substring(1);
						break;

					case 'k':
						varName = 'i' + varName.substring(1) + index++;
						break;

					default:
						throw new IllegalStateException("Unexpected varName: " + varName);
					}
				}

				return varName;
			}
			case 'J': varName = "l"; break;
			case 'S': varName = "s"; break;
			case 'Z': varName = "flag"; break;
			case 'L': {
				//First offer the remapper to suggest a name for the type
				varName = ((AsmRemapper) remapper).suggestLocalName(type, plural);
				if (isValidJavaIdentifier(varName)) break;

				// strip preceding packages and outer classes

				int start = type.lastIndexOf('/') + 1;
				int startDollar = type.lastIndexOf('$') + 1;

				if (startDollar > start && startDollar < type.length() - 1) {
					start = startDollar;
				} else if (start == 0) {
					start = 1;
				}

				// assemble, lowercase first char, apply plural s

				char first = type.charAt(start);
				char firstLc = Character.toLowerCase(first);

				if (first == firstLc) { // type is already lower case, the var name would shade the type
					varName = null;
				} else {
					varName = firstLc + type.substring(start + 1, type.length() - 1);
				}

				if (!isValidJavaIdentifier(varName)) {
					varName = isArg ? "arg" : "lv"; // lv instead of var to avoid confusion with Java 10's var keyword
				}

				break;
			}
			default:
				throw new IllegalStateException("Unexpected type: " + type);
			}

			if (plural) {
				String pluralVarName = varName + 's';

				// Appending 's' could make name invalid, e.g. "clas" -> "class" (keyword)
				if (isValidJavaIdentifier(pluralVarName)) {
					varName = pluralVarName;
				}
			}

			int count = nameCounts.compute(varName, (k, v) -> (v == null) ? 0 : v + 1);
			return count == 0 ? varName : varName.concat(Integer.toString(count));
		}

		private static boolean isValidJavaIdentifier(String s) {
			if (s == null || s.isEmpty()) return false;
			// TODO: Use SourceVersion.isKeyword(CharSequence, SourceVersion) in Java 9
			//       to make it independent from JDK version
			return SourceVersion.isIdentifier(s) && !SourceVersion.isKeyword(s);
		}

		private final String owner;
		private final MethodNode methodNode;
		private final MethodVisitor output;
		private final Map<String, Integer> nameCounts = new HashMap<>();
		private final boolean checkPackageAccess;
		private final boolean skipLocalMapping;
		private final boolean renameInvalidLocals;
	}

	private static class AsmAnnotationRemapper extends AnnotationRemapper {
		public AsmAnnotationRemapper(AnnotationVisitor annotationVisitor, Remapper remapper, String annotationDesc) {
			super(annotationVisitor, remapper);

			annotationClass = Type.getType(annotationDesc).getInternalName();
		}

		@Override
		public void visit(String name, Object value) {
			super.visit(mapAnnotationName(name, getDesc(value)), value);
		}

		private static String getDesc(Object value) {
			if (value instanceof Type) return ((Type) value).getDescriptor();

			Class<?> cls = value.getClass();

			if (Byte.class.isAssignableFrom(cls)) return "B";
			if (Boolean.class.isAssignableFrom(cls)) return "Z";
			if (Character.class.isAssignableFrom(cls)) return "C";
			if (Short.class.isAssignableFrom(cls)) return "S";
			if (Integer.class.isAssignableFrom(cls)) return "I";
			if (Long.class.isAssignableFrom(cls)) return "J";
			if (Float.class.isAssignableFrom(cls)) return "F";
			if (Double.class.isAssignableFrom(cls)) return "D";

			return Type.getDescriptor(cls);
		}

		@Override
		public void visitEnum(String name, String descriptor, String value) {
			super.visitEnum(mapAnnotationName(name, descriptor),
					descriptor,
					remapper.mapFieldName(Type.getType(descriptor).getInternalName(), value, descriptor));
		}

		@Override
		public AnnotationVisitor visitAnnotation(String name, String descriptor) {
			return createNested(descriptor, av.visitAnnotation(mapAnnotationName(name, descriptor), descriptor));
		}

		@Override
		public AnnotationVisitor visitArray(String name) {
			// try to infer the descriptor from an element

			return new AnnotationVisitor(Opcodes.ASM7) {
				@Override
				public void visit(String name, Object value) {
					if (av == null) start(getDesc(value));

					super.visit(name, value);
				}

				@Override
				public void visitEnum(String name, String descriptor, String value) {
					if (av == null) start(descriptor);

					super.visitEnum(name, descriptor, value);
				}

				@Override
				public AnnotationVisitor visitAnnotation(String name, String descriptor) {
					if (av == null) start(descriptor);

					return super.visitAnnotation(name, descriptor);
				}

				@Override
				public AnnotationVisitor visitArray(String name) {
					throw new IllegalStateException("nested arrays are disallowed by the jvm spec");
				}

				@Override
				public void visitEnd() {
					if (av == null) {
						// no element to infer from, try to find a mapping with a suitable owner+name+desc
						// there's no need to wrap the visitor in AsmAnnotationRemapper without any content to process

						String newName;

						if (name == null) { // used for default annotation values
							newName = null;
						} else {
							newName = ((AsmRemapper) remapper).mapMethodNamePrefixDesc(annotationClass, name, "()[");
						}

						av = AsmAnnotationRemapper.this.av.visitArray(newName);
					}

					super.visitEnd();
				}

				private void start(String desc) {
					assert av == null;

					desc = "["+desc;

					av = createNested(desc, AsmAnnotationRemapper.this.av.visitArray(mapAnnotationName(name, desc)));
				}
			};
		}

		private String mapAnnotationName(String name, String descriptor) {
			if (name == null) return null; // used for default annotation values

			return remapper.mapMethodName(annotationClass, name, "()"+descriptor);
		}

		private AnnotationVisitor createNested(String descriptor, AnnotationVisitor parent) {
			return AsmClassRemapper.createAsmAnnotationRemapper(descriptor, parent, remapper);
		}

		private final String annotationClass;
	}
}
