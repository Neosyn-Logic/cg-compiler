/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */


package com.neosyn.neosynide.internal.generators.verilog

import com.neosyn.models.ir.ExprBinary
import com.neosyn.neosynide.internal.generators.AssertText
import com.neosyn.core.IPathResolver
import com.neosyn.core.IProperties
import com.neosyn.models.dpn.DpnPackage
import com.neosyn.models.dpn.Entity
import com.neosyn.models.dpn.Goto
import com.neosyn.models.ir.BlockBasic
import com.neosyn.models.ir.BlockIf
import com.neosyn.models.ir.BlockWhile
import com.neosyn.models.ir.ExprVar
import com.neosyn.models.ir.Expression
import com.neosyn.models.ir.InstAssign
import com.neosyn.models.ir.InstCall
import com.neosyn.models.ir.InstLoad
import com.neosyn.models.ir.InstReturn
import com.neosyn.models.ir.InstStore
import com.neosyn.models.ir.Instruction
import com.neosyn.models.ir.Procedure
import com.neosyn.models.ir.Type
import com.neosyn.models.ir.TypeArray
import com.neosyn.models.ir.TypeFloat
import com.neosyn.models.ir.TypeInt
import com.neosyn.models.ir.TypeString
import com.neosyn.models.ir.Var
import com.neosyn.models.ir.util.TypeUtil
import com.neosyn.models.ir.util.ValueUtil
import com.neosyn.models.util.EcoreHelper
import com.neosyn.neosynide.internal.generators.Namer
import java.util.ArrayList
import java.util.List
import java.util.WeakHashMap
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtend2.lib.StringConcatenation

/**
 * This class prints Verilog code from IR.
 * 


 */
class VerilogIrPrinter extends VerilogExpressionPrinter {

	val IPathResolver pathResolver

	// Write-forwarding: tracks last non-blocking indexed store to a state array
	// so a subsequent load from the same array generates a forwarding mux
	var Var pendingStoreArray = null
	var CharSequence pendingStoreIndexExpr = null
	var CharSequence pendingStoreValueExpr = null

	/**
	 * Per IR entity, the number of assert blocks named so far. Keyed on the entity, not on this
	 * printer: several printers write into one module, and every generation builds new IR, so a
	 * regenerate numbers the same asserts the same way.
	 */
	static val checks = new WeakHashMap<Entity, Integer>

	def private static synchronized int nextCheck(EObject call) {
		val entity = EcoreHelper.getContainerOfType(call, Entity)
		val n = (checks.get(entity) ?: 0) + 1
		checks.put(entity, n)
		n
	}

	new(Namer namer, IPathResolver pathResolver) {
		super(namer);
		this.pathResolver = pathResolver
	}

	def resetWriteForwarding() {
		pendingStoreArray = null
		pendingStoreIndexExpr = null
		pendingStoreValueExpr = null
	}

	override caseBlockBasic(BlockBasic node)
		'''
		«doSwitch(node.instructions)»
		'''

	override caseBlockIf(BlockIf block)
		'''
		«printComments(block, block.lineNumber)»«printIf(block)»
		«IF block.joinBlock !== null»
		«doSwitch(block.joinBlock)»
		«ENDIF»
		'''

	/**
	 * An `else` whose only content is another `if` prints as `end else if (...) begin`, not as
	 * `end else begin` + a nested `if` one level deeper (TRAPS T116) -- so a priority chain reads as
	 * the flat chain it is. See {@link #elseIfOf} for when it is left nested.
	 */
	def private CharSequence printIf(BlockIf block) {
		val nested = elseIfOf(block)
		'''
		if («doSwitch(block.condition)») begin
		  «doSwitch(block.thenBlocks)»
		«IF nested !== null»
		end else «printIf(nested)»
		«ELSEIF block.elseRequired»
		end else begin
		  «doSwitch(block.elseBlocks)»
		end
		«ELSE»
		end
		«ENDIF»'''
	}

	/**
	 * The `if` an else branch consists of, or null: the branch holds exactly that `if` (empty blocks
	 * aside), the `if` has no join code to print after it, and no source comment of its own to print
	 * above it -- a comment would have nowhere to go on `end else if` and stays nested instead.
	 */
	def private BlockIf elseIfOf(BlockIf block) {
		if (!block.elseRequired) {
			return null
		}
		val blocks = block.elseBlocks.filter[b | !(b instanceof BlockBasic && (b as BlockBasic).instructions.empty)].toList
		if (blocks.size != 1 || !(blocks.head instanceof BlockIf)) {
			return null
		}
		val nested = blocks.head as BlockIf
		if (nested.joinBlock !== null && !nested.joinBlock.instructions.empty) {
			return null
		}
		val comments = printComments(nested, nested.lineNumber)
		if (comments !== null && comments.toString.trim.length > 0) {
			return null
		}
		nested
	}

	override caseBlockWhile(BlockWhile block)
		'''
		«printComments(block, block.lineNumber)»while («doSwitch(block.condition)») begin
		  «doSwitch(block.blocks)»
		  «doSwitch(block.joinBlock)»
		end
		'''

	override caseInstAssign(InstAssign assign) {
		'''
		«printComments(assign, assign.lineNumber)»«namer.getName(assign.target.variable)» = «doSwitch(assign.value)»;
		'''
	}

	override caseInstCall(InstCall call) {
		if (call.assert) {
			val expr = doSwitch(call.arguments.get(0))
			// The condition keeps the raw expression (escaped identifiers like `\reg `
			// and `\EXPECT ` are valid there), but when the SAME text is embedded inside
			// the $display string literal its backslashes are read as string escapes —
			// Verilator rejects e.g. `\E` as an unknown escape (iverilog is lenient).
			// Escape backslashes and quotes for the message copy only.
			// The message is the SOURCE text and where it is, as in the simulator (TRAPS T175: it was the
			// IR expression, `(v_3 == E_0_1)` -- temporaries' names, no values, no file:line), and for an
			// integer comparison the value of each side.
			val cond = call.arguments.get(0)
			val text = AssertText.where(call) + AssertText.text(cond)
			val msg = text.replace("\\", "\\\\").replace("\"", "\\\"")
			val cmp = if (AssertText.isIntComparison(cond)) cond as ExprBinary else null
			val values = if (cmp !== null)
				(" -- " + AssertText.text(cmp.e1) + " = %0d, " + AssertText.text(cmp.e2) + " = %0d")
					.replace("\\", "\\\\").replace("\"", "\\\"")
				else ""
			val valueArgs = if (cmp !== null) ", " + doSwitch(cmp.e1) + ", " + doSwitch(cmp.e2) else ""
			// `!== 1'b1`, not `~(...)`: an `if` on X takes the else branch, so `if (~(c))` let an
			// assertion whose condition is X pass silently (T121). This fails on 0, X and Z.
			//
			// The first time it PASSES it says so, once: without that line a run whose asserts never
			// executed read exactly like one where they all held, and runners called it a pass
			// (TRAPS T112). `seen` is local to its named block and starts as X, which `!== 1'b1`
			// reads as "not yet"; the block's name is fresh in the module.
			val block = namer.freshName("check")
			'''
			// synthesis translate_off
			if ((«expr») !== 1'b1) begin
			  $display("Assertion failed: «msg»«values»"«valueArgs»);
			  $stop;
			end else begin : «block»
			  reg seen;
			  if (seen !== 1'b1) begin
			    seen = 1'b1;
			    $display("[check] «msg» first passed at %0t", $time);
			  end
			end
			// synthesis translate_on
			'''
		} else if (call.print) {
			'''
			// synthesis translate_off
			$display(«printDisplayParams(call.arguments)»);
			// synthesis translate_on
			'''
		} else {
			'''
			«IF call.target === null»
			«call.procedure.name»(«printCallParams(call.arguments)»);
			«ELSE»
			«namer.getName(call.target.variable)» = «call.procedure.name»(«printCallParams(call.arguments)»);
			«ENDIF»
			'''
		}
	}

	override caseInstReturn(InstReturn instReturn) {
		if (instReturn.value !== null) {
			val procedure = EcoreHelper.getContainerOfType(instReturn, Procedure)
			'''
			«procedure.name» = «doSwitch(instReturn.value)»;
			'''
		}
	}
	
	override caseInstLoad(InstLoad load) {
		var target = load.target.variable
		val source = load.source.variable
		if (target.type.array) {
			pendingStoreArray = null
			copyArray(target, load.indexes.get(0), source)
		} else if (pendingStoreArray !== null && source === pendingStoreArray && !load.indexes.empty) {
			// Write-forwarding: the array was just stored with non-blocking <=
			// Generate forwarding mux to preserve write-first semantics
			val loadIndexExpr = doSwitch(load.indexes.get(0))
			val storeIdx = pendingStoreIndexExpr
			val storeVal = pendingStoreValueExpr
			val sourceName = namer.getName(source)
			val targetName = namer.getName(target)
			pendingStoreArray = null
			'''
			«printComments(load, load.lineNumber)»if («storeIdx» == «loadIndexExpr»)
			  «targetName» = «storeVal»;
			else
			  «targetName» = «sourceName»[«loadIndexExpr»];
			'''
		} else {
			pendingStoreArray = null
			'''
			«printComments(load, load.lineNumber)»«namer.getName(target)» = «namer.getName(source)»«printIndexes(source.type, load.indexes)»;
			'''
		}
	}

	override caseInstruction(Instruction instr) {
		if (instr instanceof Goto) {
			// Skip gotos with null targets (can occur in incomplete FSMs)
			if (instr.target === null) {
				return ''''''
			}
			'''FSM <= «instr.target.name»;'''
		}
	}

	override caseInstStore(InstStore store) {
		val target = store.target.variable
		val value = store.value
		if (value instanceof ExprVar) {
			val source = value.use.variable
			if (source.type.array) {
				return copyArray(target, store.indexes.get(0), source)
			}
		}

		val procedure = EcoreHelper.getContainerOfType(store, typeof(Procedure))
		val containingFeature = procedure.eContainingFeature
		val combinational = containingFeature == DpnPackage.Literals.ACTION__COMBINATIONAL
		val scheduler = containingFeature == DpnPackage.Literals.ACTION__SCHEDULER
		val blocking = target.local || combinational || scheduler

		// Track non-blocking indexed store to state array for write-forwarding.
		// When a load from the same array follows, we generate a forwarding mux
		// to preserve write-first semantics (e.g., register file read-after-write).
		if (!blocking && target.type.array && !store.indexes.empty) {
			pendingStoreArray = target
			pendingStoreIndexExpr = doSwitch(store.indexes.get(0))
			pendingStoreValueExpr = doSwitch(value)
		} else {
			pendingStoreArray = null
		}

		'''
		«printComments(store, store.lineNumber)»«namer.getName(target)»«printIndexes(target.type, store.indexes)» «IF !blocking»<«ENDIF»= «doSwitch(value)»;
		'''
	}

	override caseTypeArray(TypeArray type) {
		var depth = 1
		for (dim : type.dimensions ) {
			depth = depth * dim
		}
		depth = depth - 1
		'''[0 : «depth»]'''
	}

	override caseTypeFloat(TypeFloat type) '''real'''

	override caseTypeInt(TypeInt type) {
		// Guard against unresolved sizes (can happen with built-in entity ports)
		val size = if (type.size <= 0) 1 else type.size
		// if signed, prints 'signed [size - 1 : 0]', otherwise just '[size - 1 : 0]'
		'''«IF type.signed»signed «ENDIF»[«size - 1» : 0]'''
	}

	override caseTypeString(TypeString type) {
		throw new IllegalArgumentException("unsupported String type")
	}

	override caseVar(Var variable) {
		val name = namer.getName(variable)
		if (variable.type.array) {
			'''«doSwitch((variable.type as TypeArray).elementType)» «name» «doSwitch(variable.type)»'''
		} else {
			'''«doSwitch(variable.type)» «name»'''
		}
	}

	def private copyArray(Var target, Expression index, Var source) {
		// Route every identifier through the namer so reserved words (e.g. an
		// array named `buf`, which collides with the Verilog `buf` primitive
		// gate) get escaped — the raw .name here previously emitted malformed
		// Verilog for such names.
		var loopVar = namer.getName((index as ExprVar).use.variable)
		val bound = (source.type as TypeArray).dimensions.fold(1, [acc, elt|acc * elt])

		'''
		for («loopVar» = 0; «loopVar» < «bound»; «loopVar» = «loopVar» + 1) begin
		  «namer.getName(target)»[«loopVar»] «IF target.isGlobal»<«ENDIF»= «namer.getName(source)»[«loopVar»];
		end
		'''
	}

	def doSwitch(List<? extends EObject> objects)
		'''
		«FOR eObject : objects»
			«doSwitch(eObject)»
		«ENDFOR»
		'''
	
	/**
	 * Whether this array's data is emitted INLINE into the module rather than into a side
	 * {@code .hex} file. {@code VerilogCodeGenerator.printHexFiles} asks the same question before
	 * deciding whether to write that file, so the two cannot drift into disagreeing about which
	 * arrays own their data.
	 *
	 * An array with no initial value has no user data to inline. Floats print as decimals rather
	 * than hex digits, so they cannot take the sized-literal form {@code printArrayInit} emits; no
	 * current design has a float ROM, so they keep the old path rather than get something
	 * untested.
	 */
	def static boolean isArrayInlined(TypeArray type, EObject value) {
		value !== null && !(type.elementType instanceof TypeFloat)
	}

	/**
	 * Initialises an array-typed reg.
	 *
	 * An array that HAS an initial value carries user data — a coefficient table, an S-box, a
	 * character ROM, a microcode store. That data is emitted INLINE, so the generated module is
	 * self-contained. It used to be written to a side {@code .hex} file and pulled back in with
	 * {@code $readmemh} at an ABSOLUTE path fixed at generation time; move or ship the {@code .v}
	 * without that file and the ROM silently filled with {@code x} while every tool still exited 0
	 * (TRAPS T33, a severity upgrade of T14). A self-contained module cannot lose its ROM.
	 *
	 * An array with NO initial value is zeroed in place by an initial loop (S181 finding F8) — no
	 * side file, no path, and a {@code u8 ram[65536]} stays one loop rather than 65536 assignments.
	 * Only an INITIALISED array of {@code float} elements (see {@link #isArrayInlined}) still goes
	 * through a side {@code .hex} and {@code $readmemh} at an absolute path — TRAPS T14, still open.
	 */
	def private printArrayInit(Entity entity, Var variable, CharSequence name) {
		val type = variable.type as TypeArray
		val value = variable.initialValue

		if (value === null) {
			// An array with NO initial value holds nothing the user wrote. It used to be zero-filled
			// from a side .hex at an ABSOLUTE path fixed at generation time -- the only thing in a
			// generated design naming the build machine, and a file that had to ship next to the .v
			// (S181 coding-rules audit, finding F8: 20 such calls in 16 design files, every file all
			// zeros). Zero it in place instead: the same values at time 0, no file, no path.
			val n = type.dimensions.fold(1)[acc, d|acc * d]
			return '''
			initial begin : «namer.getSafeName(variable.name + "_zero_init")»
			  integer init_i;
			  for (init_i = 0; init_i < «n»; init_i = init_i + 1) begin
			    «name»[init_i] = 0;
			  end
			end
			'''
		}
		if (!isArrayInlined(type, value)) {
			// hex path uses the RAW name (VerilogCodeGenerator writes the file with
			// var.getName()); the reg reference uses the escaped name
			return '''initial $readmemh("«getAbsoluteHexPath(entity, variable.name)»", «name»);'''
		}

		val size = TypeUtil.getSize(type.elementType)
		val text = new VerilogHexPrinter(size).doSwitch(value).toString.trim
		if (text.empty) {
			return ""
		}

		// Elements are laid out linearly: caseTypeArray declares every array, whatever its
		// declared dimensions, as one flat [0 : n-1] range, and caseExprList flattens a nested
		// initialiser in that same order. So index i below addresses exactly the element that
		// line i of the .hex used to.
		val values = text.split("\\s+")
		'''
		initial begin
			«FOR i : 0 ..< values.length»
				«name»[«i»] = «size»'h«values.get(i)»;
			«ENDFOR»
		end
		'''
	}

	def private getAbsoluteHexPath(Entity entity, String varName) {
		val path = pathResolver.getFullPath(entity)
		path.substring(0, path.lastIndexOf('.')) + "_" + varName + ".hex"
	}

	def private printCallParams(List<Expression> arguments) {
		if (arguments.empty) {
			"1'b1"
		} else {
			'''«FOR expr : arguments SEPARATOR ", "»«doSwitch(expr)»«ENDFOR»'''
		}
	}

	/**
	 * True when the element printed just before {@code obj} came from the same source line, so the
	 * line's comment is already above it. One statement often becomes several instructions --
	 * `header.write(x)` is `header <= x` and `header_valid <= 1'b1` -- and an `else if` becomes an
	 * `if` nested in the else, all carrying the statement's line; the comment was printed above
	 * each. "Just before" is the previous sibling, or, for the first element of a block, the `if` or
	 * `while` that encloses it. Local to the IR tree on purpose: a comment printed once is never
	 * lost (a stateful "already printed" set was, whenever something rendered without emitting).
	 */
	def private boolean continuesLine(EObject obj, int lineNumber) {
		var EObject e = obj
		while (e !== null && !(e instanceof Procedure)) {
			val container = e.eContainer
			val feature = e.eContainingFeature
			if (container !== null && feature !== null && feature.many) {
				val list = container.eGet(feature) as java.util.List<?>
				val idx = list.indexOf(e)
				if (idx > 0) {
					return lastLine(list.get(idx - 1) as EObject) == lineNumber
				}
			}
			if (container instanceof BlockIf) {
				return container.lineNumber == lineNumber
			}
			if (container instanceof BlockWhile) {
				return container.lineNumber == lineNumber
			}
			e = container
		}
		false
	}

	/** The source line of the last thing printed for {@code e}; -1 when it prints nothing. */
	def private int lastLine(EObject e) {
		switch (e) {
			Instruction: e.lineNumber
			BlockIf: e.lineNumber
			BlockWhile: e.lineNumber
			// get(size - 1), not `.last`: on a Java 21 JDK Xtend binds `.last` to List.getLast(),
			// which the JavaSE-17 bundles cannot compile against where the build is strict (the
			// release build machine refused it; the nightly's did not notice)
			BlockBasic: if (e.instructions.empty) -1 else e.instructions.get(e.instructions.size - 1).lineNumber
			default: -1
		}
	}

	def printComments(EObject obj, int lineNumber) {
		val entity = EcoreHelper.getContainerOfType(obj, Entity)
		val comments = entity.properties.getAsJsonObject(IProperties.PROP_COMMENTS)
		if (comments !== null) {
			val lines = comments.getAsJsonArray(String.valueOf(lineNumber))
			if (lines !== null && lines.size != 0 && !continuesLine(obj, lineNumber)) {
				'''
				«FOR line : lines»
				// «line.asString»
				«ENDFOR»
				'''
			}
		}
	}

	def private printDisplayParams(List<Expression> arguments) {
		val format = new StringConcatenation
		val args = new ArrayList<CharSequence>
		format.append("\"")
		args.add(format)
		for (expr : arguments) {
			if (expr.exprString) {
				format.append(doSwitch(expr))
			} else {
				format.append("%0h")
				args.add(doSwitch(expr))
			}
		}

		format.append("\"")
		args.join(", ")
	}
	
	def printFunction(Procedure procedure) {
		'''	
		function «doSwitch(procedure.returnType)» «procedure.name»(«IF procedure.parameters.empty»input _dummy«ELSE»«FOR param : procedure.parameters SEPARATOR ', '»input «doSwitch(param)»«ENDFOR»«ENDIF»);
		  «FOR local : procedure.locals»
		  reg «doSwitch(local)»;
		  «ENDFOR»
		  begin
		    «doSwitch(procedure.blocks)»
		  end
		endfunction

		'''
	}

	/**
	 * An array is declared as one flat [0 : n-1] range laid out row-major (see printArrayInit), so
	 * a[i][j] of a [D0][D1] array is element i*D1 + j. Concatenating the index bits, {i, j}, is that
	 * same number only when every inner dimension is a power of two -- the common case, printed as
	 * before. TRAPS T185: for u8 a[3][5], {i, j} = i*8 + j put row 1 on the wrong elements and row 2
	 * past the end of the array (iverilog read x); such an array now gets the row-major arithmetic.
	 */
	def private printIndexes(Type type, List<Expression> indexes) {
		val it = indexes.iterator
		if (it.hasNext) {
			val firstIndex = it.next
			if (it.hasNext) {
				val dims = (type as TypeArray).dimensions
				if (dims.size == indexes.size && !dims.drop(1).forall[d|ValueUtil.isPowerOfTwo(d)]) {
					'''[«rowMajorIndex(dims, indexes.map[index|doSwitch(index)])»]'''
				} else {
					'''[{«indexes.map[index|doSwitch(index)].join(', ')»}]'''
				}
			} else {
				'''[«doSwitch(firstIndex)»]'''
			}
		}
	}

	/** ((i0) * D1 + (i1)) * D2 + (i2) ...: the unsized literals make the index arithmetic 32 bits wide. */
	def private CharSequence rowMajorIndex(List<Integer> dims, List<CharSequence> indexes) {
		var CharSequence flat = indexes.head
		for (k : 1 ..< indexes.size) {
			flat = '''(«flat») * «dims.get(k)» + («indexes.get(k)»)'''
		}
		flat
	}

	/**
	 * prints the initial value of the given variable. If the variable has no
	 * initial value, the value that corresponds to the neutral element of
	 * the variable's type is returned.
	 * 
	 * variable is never an array.
	 */
	def printInitialValue(Var variable) {
		val type = variable.type
		val value = variable.initialValue
		if (value === null) {
			'''«TypeUtil.getSize(type)»'b0'''
		} else {
			doSwitch(value)
		}
	}

	def printStateVar(Entity entity, Var variable) {
		val name = namer.getName(variable)

		'''
		«IF variable.assignable || variable.type.array»
			reg «doSwitch(variable)»;
			«IF variable.type.array»
				«printArrayInit(entity, variable, name)»
			«ENDIF»
		«ELSE»
			localparam «doSwitch(variable)» = «printInitialValue(variable)»;
		«ENDIF»
		'''
	}

}
