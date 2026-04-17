package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.MetaGrammar;
import org.junit.jupiter.api.Test;

public class JavascriptGrammarTest {
  private static final MetaGrammar META_GRAMMAR =
      MetaGrammar.load("""
          tokens {
            // ----- Literals -----
            NullLiteral:              /null/
            BooleanLiteral:           /true|false/
            DecimalLiteral:           /(?:0|[1-9][0-9]*)(?:\\.[0-9]*)?(?:[eE][+-]?[0-9]+)?|\\.[0-9]+(?:[eE][+-]?[0-9]+)?/
            HexIntegerLiteral:        /0[xX][0-9a-fA-F]+/
            OctalIntegerLiteral:      /0[0-7]+/
            OctalIntegerLiteral2:     /0[oO][0-7]+/
            BinaryIntegerLiteral:     /0[bB][01]+/
            BigDecimalIntegerLiteral: /(?:0|[1-9][0-9]*)n/
            BigHexIntegerLiteral:     /0[xX][0-9a-fA-F]+n/
            BigOctalIntegerLiteral:   /0[oO][0-7]+n/
            BigBinaryIntegerLiteral:  /0[bB][01]+n/
            StringLiteral:            /"(?:[^"\\\\]|\\\\.)*"|'(?:[^'\\\\]|\\\\.)*'/
            RegularExpressionLiteral: /\\/(?:[^\\/\\\\\\n]|\\\\.)+\\/[gimsuy]*/
            BackTick:                 /`/
            TemplateStringAtom:       /(?:[^`\\\\$]|\\\\.|\\$(?!\\{))+/
            TemplateStringStartExpression: /\\$\\{/
            TemplateCloseBrace:       /\\}/
          
            // ----- Keywords (must appear before Identifier) -----
            Break:       /break/
            Case:        /case/
            Catch:       /catch/
            Class:       /class/
            Const:       /const/
            Continue:    /continue/
            Debugger:    /debugger/
            Default:     /default/
            Delete:      /delete/
            Do:          /do/
            Else:        /else/
            Enum:        /enum/
            Export:      /export/
            Extends:     /extends/
            Finally:     /finally/
            For:         /for/
            Function_:   /function/
            If:          /if/
            Import:      /import/
            In:          /in/
            Instanceof:  /instanceof/
            New:         /new/
            Return:      /return/
            Super:       /super/
            Switch:      /switch/
            This:        /this/
            Throw:       /throw/
            Try:         /try/
            Typeof:      /typeof/
            Var:         /var/
            Void:        /void/
            While:       /while/
            With:        /with/
            Yield:       /yield/
            YieldStar:   /yield\\*/
            Async:       /async/
            Await:       /await/
            StrictLet:   /let/
            NonStrictLet: /let/
            From:        /from/
            As:          /as/
            Of:          /of/
            Static:      /static/
            Implements:  /implements/
            Private:     /private/
            Public:      /public/
            Interface:   /interface/
            Package:     /package/
            Protected:   /protected/
          
            // ----- Identifiers -----
            Identifier: /[A-Za-z_$][A-Za-z0-9_$]*/
          
            // ----- Multi-character operators (must precede single-char) -----
            op_stricteq:    /===/
            op_strictne:    /!==/
            op_eq:          /==/
            op_ne:          /!=/
            op_le:          /<=/
            op_ge:          />=/
            op_shl:         /<</
            op_ushr:        />>>/
            op_shr:         />>/
            op_pow:         /\\*\\*/
            op_nullish:     /\\?\\?/
            op_optchain:    /\\?\\./
            op_ellipsis:    /\\.\\.\\./
            op_arrow:       /=>/
            op_muleq:       /\\*=/
            op_diveq:       /\\/=/
            op_modeq:       /%=/
            op_addeq:       /\\+=/
            op_subeq:       /-=/
            op_shleq:       /<<=/
            op_shreq:       />>=/
            op_ushreq:      />>>=/
            op_andeq:       /&=/
            op_xoreq:       /\\^=/
            op_oreq:        /\\|=/
            op_poweq:       /\\*\\*=/
            op_nullisheq:   /\\?\\?=/
            op_logand:      /&&/
            op_logor:       /\\|\\|/
            op_inc:         /\\+\\+/
            op_dec:         /--/
          
            // ----- Whitespace & comments (ignored) -----
            /[ \\t\\r\\n]+/
            /\\/\\/[^\\n]*/
            /\\/\\*(?:[^*]|\\*[^\\/])*\\*\\//
            /#![^\\n]*/
          }
          
          // ============================================================
          //  Precedence (lowest → highest, matching JS operator table)
          // ============================================================
          precedence {
            right: '='
            right: op_muleq, op_diveq, op_modeq, op_addeq, op_subeq
            right: op_shleq, op_shreq, op_ushreq, op_andeq, op_xoreq, op_oreq, op_poweq, op_nullisheq
            right: '?'
            left:  op_logor
            left:  op_logand
            left:  op_nullish
            left:  '|'
            left:  '^'
            left:  '&'
            left:  op_eq, op_ne, op_stricteq, op_strictne
            left:  '<', '>', op_le, op_ge, In, Instanceof
            left:  op_shl, op_shr, op_ushr
            left:  '+', '-'
            left:  '*', '/', '%'
            right: op_pow
            right: UNARY
            left:  op_inc, op_dec
            left:  '.', op_optchain, '['
            left:  New
          }
          
          // ============================================================
          //  Grammar
          // ============================================================
          grammar {
          
            // -------------------------------------------------------
            //  Top-level
            // -------------------------------------------------------
            Program : SourceElements
            Program :
          
            SourceElements : SourceElement
            SourceElements : SourceElements SourceElement
          
            SourceElement : Statement
          
            // -------------------------------------------------------
            //  Statements
            // -------------------------------------------------------
            Statement : Block
            Statement : VariableStatement
            Statement : ImportStatement
            Statement : ExportStatement
            Statement : EmptyStatement
            Statement : ClassDeclaration
            Statement : FunctionDeclaration
            Statement : ExpressionStatement
            Statement : IfStatement
            Statement : IterationStatement
            Statement : ContinueStatement
            Statement : BreakStatement
            Statement : ReturnStatement
            Statement : YieldStatement
            Statement : WithStatement
            Statement : LabelledStatement
            Statement : SwitchStatement
            Statement : ThrowStatement
            Statement : TryStatement
            Statement : DebuggerStatement
          
            // -------------------------------------------------------
            //  Block
            // -------------------------------------------------------
            Block : '{' StatementList '}'
            Block : '{' '}'
          
            StatementList : Statement
            StatementList : StatementList Statement
          
            // -------------------------------------------------------
            //  Import
            // -------------------------------------------------------
            ImportStatement : Import ImportFromBlock
          
            ImportFromBlock : ImportDefault ImportNamespace ImportFrom Eos
            ImportFromBlock : ImportDefault ImportModuleItems ImportFrom Eos
            ImportFromBlock : ImportNamespace ImportFrom Eos
            ImportFromBlock : ImportModuleItems ImportFrom Eos
            ImportFromBlock : StringLiteral Eos
          
            ImportModuleItems : '{' '}'
            ImportModuleItems : '{' ImportAliasNameList '}'
            ImportModuleItems : '{' ImportAliasNameList ',' '}'
          
            ImportAliasNameList : ImportAliasName
            ImportAliasNameList : ImportAliasNameList ',' ImportAliasName
          
            ImportAliasName : ModuleExportName
            ImportAliasName : ModuleExportName As ImportedBinding
          
            ModuleExportName : IdentifierName
            ModuleExportName : StringLiteral
          
            ImportedBinding : Identifier
            ImportedBinding : Yield
            ImportedBinding : Await
          
            ImportDefault : AliasName ','
          
            ImportNamespace : '*' As IdentifierName
            ImportNamespace : IdentifierName As IdentifierName
            ImportNamespace : '*'
            ImportNamespace : IdentifierName
          
            ImportFrom : From StringLiteral
          
            AliasName : IdentifierName As IdentifierName
            AliasName : IdentifierName
          
            // -------------------------------------------------------
            //  Export
            // -------------------------------------------------------
            ExportStatement : Export Default ExportFromBlock Eos
            ExportStatement : Export ExportFromBlock Eos
            ExportStatement : Export Default Declaration Eos
            ExportStatement : Export Declaration Eos
            ExportStatement : Export Default SingleExpression Eos
          
            ExportFromBlock : ImportNamespace ImportFrom Eos
            ExportFromBlock : ExportModuleItems ImportFrom Eos
            ExportFromBlock : ExportModuleItems Eos
          
            ExportModuleItems : '{' '}'
            ExportModuleItems : '{' ExportAliasNameList '}'
            ExportModuleItems : '{' ExportAliasNameList ',' '}'
          
            ExportAliasNameList : ExportAliasName
            ExportAliasNameList : ExportAliasNameList ',' ExportAliasName
          
            ExportAliasName : ModuleExportName As ModuleExportName
            ExportAliasName : ModuleExportName
          
            Declaration : VariableStatement
            Declaration : ClassDeclaration
            Declaration : FunctionDeclaration
          
            // -------------------------------------------------------
            //  Variable
            // -------------------------------------------------------
            VariableStatement : VariableDeclarationList Eos
          
            VariableDeclarationList : VarModifier VariableDeclaration
            VariableDeclarationList : VariableDeclarationList ',' VariableDeclaration
          
            SingleVariableDeclaration : VarModifier VariableDeclaration
          
            VariableDeclaration : Assignable '=' SingleExpression
            VariableDeclaration : Assignable
          
            // -------------------------------------------------------
            //  Simple statements
            // -------------------------------------------------------
            EmptyStatement : ';'
          
            // Note: ANTLR's {this.notOpenBraceAndNotFunction()}? predicate must be
            // enforced in the evaluator; grammar cannot express it.
            ExpressionStatement : ExpressionSequence Eos
          
            IfStatement : If '(' ExpressionSequence ')' Statement Else Statement
            IfStatement : If '(' ExpressionSequence ')' Statement
          
            // -------------------------------------------------------
            //  Iteration
            // -------------------------------------------------------
            IterationStatement : Do Statement While '(' ExpressionSequence ')' Eos
            IterationStatement : While '(' ExpressionSequence ')' Statement
            IterationStatement : For '(' ExpressionSequence ';' ExpressionSequence ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' ExpressionSequence ';' ExpressionSequence ';' ')' Statement
            IterationStatement : For '(' ExpressionSequence ';' ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' ExpressionSequence ';' ';' ')' Statement
            IterationStatement : For '(' VariableDeclarationList ';' ExpressionSequence ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' VariableDeclarationList ';' ExpressionSequence ';' ')' Statement
            IterationStatement : For '(' VariableDeclarationList ';' ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' VariableDeclarationList ';' ';' ')' Statement
            IterationStatement : For '(' ';' ExpressionSequence ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' ';' ExpressionSequence ';' ')' Statement
            IterationStatement : For '(' ';' ';' ExpressionSequence ')' Statement
            IterationStatement : For '(' ';' ';' ')' Statement
            IterationStatement : For '(' SingleExpression In ExpressionSequence ')' Statement
            IterationStatement : For '(' SingleVariableDeclaration In ExpressionSequence ')' Statement
            IterationStatement : For '(' SingleExpression Of ExpressionSequence ')' Statement
            IterationStatement : For '(' SingleVariableDeclaration Of ExpressionSequence ')' Statement
            IterationStatement : For Await '(' SingleExpression Of ExpressionSequence ')' Statement
            IterationStatement : For Await '(' SingleVariableDeclaration Of ExpressionSequence ')' Statement
          
            VarModifier : Var
            VarModifier : StrictLet
            VarModifier : NonStrictLet
            VarModifier : Const
          
            // Note: {this.notLineTerminator()}? on identifier must be enforced in evaluator
            ContinueStatement : Continue Identifier Eos
            ContinueStatement : Continue Eos
          
            BreakStatement : Break Identifier Eos
            BreakStatement : Break Eos
          
            ReturnStatement : Return ExpressionSequence Eos
            ReturnStatement : Return Eos
          
            YieldStatement : Yield ExpressionSequence Eos
            YieldStatement : Yield Eos
            YieldStatement : YieldStar ExpressionSequence Eos
            YieldStatement : YieldStar Eos
          
            WithStatement : With '(' ExpressionSequence ')' Statement
          
            SwitchStatement : Switch '(' ExpressionSequence ')' CaseBlock
          
            CaseBlock : '{' '}'
            CaseBlock : '{' CaseClauses '}'
            CaseBlock : '{' DefaultClause '}'
            CaseBlock : '{' CaseClauses DefaultClause '}'
            CaseBlock : '{' CaseClauses DefaultClause CaseClauses '}'
          
            CaseClauses : CaseClause
            CaseClauses : CaseClauses CaseClause
          
            CaseClause : Case ExpressionSequence ':' StatementList
            CaseClause : Case ExpressionSequence ':'
          
            DefaultClause : Default ':' StatementList
            DefaultClause : Default ':'
          
            LabelledStatement : Identifier ':' Statement
          
            // Note: {this.notLineTerminator()}? must be enforced in evaluator
            ThrowStatement : Throw ExpressionSequence Eos
          
            TryStatement : Try Block CatchProduction FinallyProduction
            TryStatement : Try Block CatchProduction
            TryStatement : Try Block FinallyProduction
          
            CatchProduction : Catch '(' Assignable ')' Block
            CatchProduction : Catch '(' ')' Block
            CatchProduction : Catch Block
          
            FinallyProduction : Finally Block
          
            DebuggerStatement : Debugger Eos
          
            // -------------------------------------------------------
            //  Function & Class declarations
            // -------------------------------------------------------
            FunctionDeclaration : Async Function_ '*' Identifier '(' FormalParameterList ')' FunctionBody
            FunctionDeclaration : Async Function_ '*' Identifier '(' ')' FunctionBody
            FunctionDeclaration : Async Function_ Identifier '(' FormalParameterList ')' FunctionBody
            FunctionDeclaration : Async Function_ Identifier '(' ')' FunctionBody
            FunctionDeclaration : Function_ '*' Identifier '(' FormalParameterList ')' FunctionBody
            FunctionDeclaration : Function_ '*' Identifier '(' ')' FunctionBody
            FunctionDeclaration : Function_ Identifier '(' FormalParameterList ')' FunctionBody
            FunctionDeclaration : Function_ Identifier '(' ')' FunctionBody
          
            ClassDeclaration : Class Identifier ClassTail
          
            ClassTail : Extends SingleExpression '{' ClassElementList '}'
            ClassTail : Extends SingleExpression '{' '}'
            ClassTail : '{' ClassElementList '}'
            ClassTail : '{' '}'
          
            ClassElementList : ClassElement
            ClassElementList : ClassElementList ClassElement
          
            ClassElement : Static MethodDefinition
            ClassElement : Static FieldDefinition
            ClassElement : Static Block
            ClassElement : MethodDefinition
            ClassElement : FieldDefinition
            ClassElement : EmptyStatement
            // Note: {this.n("static")}? identifier cases require semantic disambiguation
          
            MethodDefinition : Async '*' ClassElementName '(' FormalParameterList ')' FunctionBody
            MethodDefinition : Async '*' ClassElementName '(' ')' FunctionBody
            MethodDefinition : Async ClassElementName '(' FormalParameterList ')' FunctionBody
            MethodDefinition : Async ClassElementName '(' ')' FunctionBody
            MethodDefinition : '*' ClassElementName '(' FormalParameterList ')' FunctionBody
            MethodDefinition : '*' ClassElementName '(' ')' FunctionBody
            MethodDefinition : ClassElementName '(' FormalParameterList ')' FunctionBody
            MethodDefinition : ClassElementName '(' ')' FunctionBody
            MethodDefinition : '*' Getter '(' ')' FunctionBody
            MethodDefinition : Getter '(' ')' FunctionBody
            MethodDefinition : '*' Setter '(' FormalParameterList ')' FunctionBody
            MethodDefinition : Setter '(' FormalParameterList ')' FunctionBody
          
            FieldDefinition : ClassElementName Initializer
            FieldDefinition : ClassElementName
          
            ClassElementName : PropertyName
            ClassElementName : PrivateIdentifier
          
            PrivateIdentifier : '#' IdentifierName
          
            FormalParameterList : FormalParameterArgs LastFormalParameterArg
            FormalParameterList : FormalParameterArgs ','
            FormalParameterList : FormalParameterArgs
            FormalParameterList : LastFormalParameterArg
          
            FormalParameterArgs : FormalParameterArg
            FormalParameterArgs : FormalParameterArgs ',' FormalParameterArg
          
            FormalParameterArg : Assignable '=' SingleExpression
            FormalParameterArg : Assignable
          
            LastFormalParameterArg : op_ellipsis SingleExpression
          
            FunctionBody : '{' SourceElements '}'
            FunctionBody : '{' '}'
          
            // -------------------------------------------------------
            //  Array & Object Literals
            // -------------------------------------------------------
            ArrayLiteral : '[' ElementList ']'
          
            // ElementList allows leading/trailing/consecutive commas
            ElementList : CommaList
            ElementList : CommaList ArrayElement CommaAndElementList
            ElementList : ArrayElement CommaAndElementList
            ElementList : ArrayElement
            ElementList :
          
            CommaList : ','
            CommaList : CommaList ','
          
            CommaAndElementList : CommaList ArrayElement CommaAndElementList
            CommaAndElementList : CommaList ArrayElement
            CommaAndElementList : CommaList
          
            ArrayElement : op_ellipsis SingleExpression
            ArrayElement : SingleExpression
          
            ObjectLiteral : '{' '}'
            ObjectLiteral : '{' PropertyAssignmentList '}'
            ObjectLiteral : '{' PropertyAssignmentList ',' '}'
          
            PropertyAssignmentList : PropertyAssignment
            PropertyAssignmentList : PropertyAssignmentList ',' PropertyAssignment
          
            PropertyAssignment : PropertyName ':' SingleExpression
            PropertyAssignment : '[' SingleExpression ']' ':' SingleExpression
            PropertyAssignment : Async '*' PropertyName '(' FormalParameterList ')' FunctionBody
            PropertyAssignment : Async '*' PropertyName '(' ')' FunctionBody
            PropertyAssignment : Async PropertyName '(' FormalParameterList ')' FunctionBody
            PropertyAssignment : Async PropertyName '(' ')' FunctionBody
            PropertyAssignment : '*' PropertyName '(' FormalParameterList ')' FunctionBody
            PropertyAssignment : '*' PropertyName '(' ')' FunctionBody
            PropertyAssignment : PropertyName '(' FormalParameterList ')' FunctionBody
            PropertyAssignment : PropertyName '(' ')' FunctionBody
            PropertyAssignment : Getter '(' ')' FunctionBody
            PropertyAssignment : Setter '(' FormalParameterArg ')' FunctionBody
            PropertyAssignment : op_ellipsis SingleExpression
            PropertyAssignment : SingleExpression
          
            PropertyName : IdentifierName
            PropertyName : StringLiteral
            PropertyName : NumericLiteral
            PropertyName : '[' SingleExpression ']'
          
            Arguments : '(' ')'
            Arguments : '(' ArgumentList ')'
            Arguments : '(' ArgumentList ',' ')'
          
            ArgumentList : Argument
            ArgumentList : ArgumentList ',' Argument
          
            Argument : op_ellipsis SingleExpression
            Argument : op_ellipsis Identifier
            Argument : SingleExpression
            Argument : Identifier
          
            // -------------------------------------------------------
            //  Expressions
            // -------------------------------------------------------
            ExpressionSequence : SingleExpression
            ExpressionSequence : ExpressionSequence ',' SingleExpression
          
            SingleExpression : AnonymousFunction
            SingleExpression : Class Identifier ClassTail
            SingleExpression : Class ClassTail
            // OptionalChain
            SingleExpression : SingleExpression op_optchain SingleExpression
            // Member access
            SingleExpression : SingleExpression op_optchain '[' ExpressionSequence ']'
            SingleExpression : SingleExpression '?.' '[' ExpressionSequence ']'
            SingleExpression : SingleExpression '[' ExpressionSequence ']'
            SingleExpression : SingleExpression op_optchain '.' '#' IdentifierName
            SingleExpression : SingleExpression op_optchain '.' IdentifierName
            SingleExpression : SingleExpression '?.' '#' IdentifierName
            SingleExpression : SingleExpression '?.' IdentifierName
            SingleExpression : SingleExpression '.' '#' IdentifierName
            SingleExpression : SingleExpression '.' IdentifierName
            // new
            SingleExpression : New Identifier Arguments
            SingleExpression : New SingleExpression Arguments
            SingleExpression : New SingleExpression
            SingleExpression : New '.' Identifier
            // Call
            SingleExpression : SingleExpression Arguments
            // Post-increment / post-decrement (note: notLineTerminator enforced externally)
            SingleExpression : SingleExpression op_inc
            SingleExpression : SingleExpression op_dec
            // Unary prefix
            SingleExpression : Delete SingleExpression                           %prec UNARY
            SingleExpression : Void SingleExpression                             %prec UNARY
            SingleExpression : Typeof SingleExpression                           %prec UNARY
            SingleExpression : op_inc SingleExpression                           %prec UNARY
            SingleExpression : op_dec SingleExpression                           %prec UNARY
            SingleExpression : '+' SingleExpression                              %prec UNARY
            SingleExpression : '-' SingleExpression                              %prec UNARY
            SingleExpression : '~' SingleExpression                              %prec UNARY
            SingleExpression : '!' SingleExpression                              %prec UNARY
            SingleExpression : Await SingleExpression                            %prec UNARY
            // Binary operators (right-to-left)
            SingleExpression : SingleExpression op_pow SingleExpression
            // Multiplicative
            SingleExpression : SingleExpression '*' SingleExpression
            SingleExpression : SingleExpression '/' SingleExpression
            SingleExpression : SingleExpression '%' SingleExpression
            // Additive
            SingleExpression : SingleExpression '+' SingleExpression
            SingleExpression : SingleExpression '-' SingleExpression
            // Nullish coalescing
            SingleExpression : SingleExpression op_nullish SingleExpression
            // Bit shift
            SingleExpression : SingleExpression op_shl SingleExpression
            SingleExpression : SingleExpression op_shr SingleExpression
            SingleExpression : SingleExpression op_ushr SingleExpression
            // Relational
            SingleExpression : SingleExpression '<' SingleExpression
            SingleExpression : SingleExpression '>' SingleExpression
            SingleExpression : SingleExpression op_le SingleExpression
            SingleExpression : SingleExpression op_ge SingleExpression
            SingleExpression : SingleExpression Instanceof SingleExpression
            SingleExpression : SingleExpression In SingleExpression
            // Equality
            SingleExpression : SingleExpression op_eq SingleExpression
            SingleExpression : SingleExpression op_ne SingleExpression
            SingleExpression : SingleExpression op_stricteq SingleExpression
            SingleExpression : SingleExpression op_strictne SingleExpression
            // Bitwise
            SingleExpression : SingleExpression '&' SingleExpression
            SingleExpression : SingleExpression '^' SingleExpression
            SingleExpression : SingleExpression '|' SingleExpression
            // Logical
            SingleExpression : SingleExpression op_logand SingleExpression
            SingleExpression : SingleExpression op_logor SingleExpression
            // Ternary
            SingleExpression : SingleExpression '?' SingleExpression ':' SingleExpression
            // Assignment (right-associative)
            SingleExpression : SingleExpression '=' SingleExpression
            SingleExpression : SingleExpression AssignmentOperator SingleExpression
            // Dynamic import
            SingleExpression : Import '(' SingleExpression ')'
            // Tagged template
            SingleExpression : SingleExpression TemplateStringLiteral
            // Yield as expression
            SingleExpression : YieldStatement
            // Primaries
            SingleExpression : This
            SingleExpression : Identifier
            SingleExpression : Super
            SingleExpression : Literal
            SingleExpression : ArrayLiteral
            SingleExpression : ObjectLiteral
            SingleExpression : '(' ExpressionSequence ')'
          
            Initializer : '=' SingleExpression
          
            Assignable : Identifier
            Assignable : Keyword
            Assignable : ArrayLiteral
            Assignable : ObjectLiteral
          
            AnonymousFunction : FunctionDeclaration
            AnonymousFunction : Async Function_ '*' '(' FormalParameterList ')' FunctionBody
            AnonymousFunction : Async Function_ '*' '(' ')' FunctionBody
            AnonymousFunction : Async Function_ '(' FormalParameterList ')' FunctionBody
            AnonymousFunction : Async Function_ '(' ')' FunctionBody
            AnonymousFunction : Function_ '*' '(' FormalParameterList ')' FunctionBody
            AnonymousFunction : Function_ '*' '(' ')' FunctionBody
            AnonymousFunction : Function_ '(' FormalParameterList ')' FunctionBody
            AnonymousFunction : Function_ '(' ')' FunctionBody
            AnonymousFunction : Async ArrowFunctionParameters '=>' ArrowFunctionBody
            AnonymousFunction : ArrowFunctionParameters '=>' ArrowFunctionBody
          
            ArrowFunctionParameters : PropertyName
            ArrowFunctionParameters : '(' FormalParameterList ')'
            ArrowFunctionParameters : '(' ')'
          
            ArrowFunctionBody : SingleExpression
            ArrowFunctionBody : FunctionBody
          
            AssignmentOperator : op_muleq
            AssignmentOperator : op_diveq
            AssignmentOperator : op_modeq
            AssignmentOperator : op_addeq
            AssignmentOperator : op_subeq
            AssignmentOperator : op_shleq
            AssignmentOperator : op_shreq
            AssignmentOperator : op_ushreq
            AssignmentOperator : op_andeq
            AssignmentOperator : op_xoreq
            AssignmentOperator : op_oreq
            AssignmentOperator : op_poweq
            AssignmentOperator : op_nullisheq
          
            // -------------------------------------------------------
            //  Literals
            // -------------------------------------------------------
            Literal : NullLiteral
            Literal : BooleanLiteral
            Literal : StringLiteral
            Literal : TemplateStringLiteral
            Literal : RegularExpressionLiteral
            Literal : NumericLiteral
            Literal : BigintLiteral
          
            TemplateStringLiteral : BackTick TemplateStringAtoms BackTick
            TemplateStringLiteral : BackTick BackTick
          
            TemplateStringAtoms : TemplateStringAtom
            TemplateStringAtoms : TemplateStringAtoms TemplateStringAtom
          
            TemplateStringAtom : TemplateStringAtom
            TemplateStringAtom : TemplateStringStartExpression SingleExpression TemplateCloseBrace
          
            NumericLiteral : DecimalLiteral
            NumericLiteral : HexIntegerLiteral
            NumericLiteral : OctalIntegerLiteral
            NumericLiteral : OctalIntegerLiteral2
            NumericLiteral : BinaryIntegerLiteral
          
            BigintLiteral : BigDecimalIntegerLiteral
            BigintLiteral : BigHexIntegerLiteral
            BigintLiteral : BigOctalIntegerLiteral
            BigintLiteral : BigBinaryIntegerLiteral
          
            // -------------------------------------------------------
            //  Getter / Setter
            //  Note: ANTLR uses {this.n("get")}? / {this.n("set")}? semantic predicates.
            //  In LazyLR these are just identifier terminals named "get"/"set".
            // -------------------------------------------------------
            Getter : Identifier ClassElementName
            Setter : Identifier ClassElementName
          
            // -------------------------------------------------------
            //  Identifier names (terminals + keywords)
            // -------------------------------------------------------
            IdentifierName : Identifier
            IdentifierName : ReservedWord
          
            Identifier : Identifier
            Identifier : NonStrictLet
            Identifier : Async
            Identifier : As
            Identifier : From
            Identifier : Yield
            Identifier : Of
          
            ReservedWord : Keyword
            ReservedWord : NullLiteral
            ReservedWord : BooleanLiteral
          
            Keyword : Break
            Keyword : Do
            Keyword : Instanceof
            Keyword : Typeof
            Keyword : Case
            Keyword : Else
            Keyword : New
            Keyword : Var
            Keyword : Catch
            Keyword : Finally
            Keyword : Return
            Keyword : Void
            Keyword : Continue
            Keyword : For
            Keyword : Switch
            Keyword : While
            Keyword : Debugger
            Keyword : Function_
            Keyword : This
            Keyword : With
            Keyword : Default
            Keyword : If
            Keyword : Throw
            Keyword : Delete
            Keyword : In
            Keyword : Try
            Keyword : Class
            Keyword : Enum
            Keyword : Extends
            Keyword : Super
            Keyword : Const
            Keyword : Export
            Keyword : Import
            Keyword : Implements
            Keyword : StrictLet
            Keyword : NonStrictLet
            Keyword : Private
            Keyword : Public
            Keyword : Interface
            Keyword : Package
            Keyword : Protected
            Keyword : Static
            Keyword : Yield
            Keyword : YieldStar
            Keyword : Async
            Keyword : Await
            Keyword : From
            Keyword : As
            Keyword : Of
          
            // -------------------------------------------------------
            //  End of statement (simplified — semantic cases not expressible)
            //  The original eos rule uses {this.lineTerminatorAhead()}? and
            //  {this.closeBrace()}? which require lookahead outside the grammar.
            //  Only the syntactic cases are expressed here.
            // -------------------------------------------------------
            Eos : ';'
          }
          """);

  @Test
  public void test() {
    META_GRAMMAR.verify();
  }
}
