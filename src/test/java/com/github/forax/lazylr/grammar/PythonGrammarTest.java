package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PythonGrammarTest {
  private static final MetaGrammar META_GRAMMAR =
      MetaGrammar.load("""
          tokens {
            NAME:         /[A-Za-z_][A-Za-z_0-9]*/
            NUMBER:       /(?:0[xX][0-9a-fA-F]+|0[bB][01]+|0[oO][0-7]+|(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?[jJ]?)/
            STRING:       /(?:[bBuU]?[rR]?|[rR]?[bBuU]?)(?:""\"[\\s\\S]*?""\"|'''[\\s\\S]*?'''|"(?:[^"\\\\\\n]|\\\\.)*"|'(?:[^'\\\\\\n]|\\\\.)*')/
            TYPE_COMMENT: /#\\s*type:\\s*[^\\n]*/
            NEWLINE:      /\\r?\\n/
            INDENT:       /(?<=\\n)[ \\t]+/
            DEDENT:       /(?<=\\n)(?![ \\t])/
            /[ \\t]+/
            /#[^\\n]*/
          }
          
          grammar {
          
            // ── Starting rule ─────────────────────────────────────────────────────
            // LazyLR has a single start symbol; the original Python grammar has
            // four entry points (file, interactive, eval, func_type).
            // We unify them here so all four remain reachable and verifiable.
          
            start : file
            start : interactive
            start : eval
            start : func_type
          
            file : statements ENDMARKER
            file : ENDMARKER
          
            interactive : statement_newline
          
            eval : expressions ENDMARKER
            eval : expressions newlines ENDMARKER
          
            func_type : '(' ')' '->' expression NEWLINE ENDMARKER
            func_type : '(' ')' '->' expression newlines ENDMARKER
            func_type : '(' type_expressions ')' '->' expression NEWLINE ENDMARKER
            func_type : '(' type_expressions ')' '->' expression newlines ENDMARKER
          
            // Zero or more NEWLINEs
            newlines : NEWLINE
            newlines : newlines NEWLINE
          
            // ── General statements ───────────────────────────────────────────────
          
            statements : statement
            statements : statements statement
          
            statement : compound_stmt
            statement : simple_stmts
          
            statement_newline : compound_stmt NEWLINE
            statement_newline : simple_stmts
            statement_newline : NEWLINE
            statement_newline : ENDMARKER
          
            simple_stmts : simple_stmt NEWLINE
            simple_stmts : simple_stmt_list ';' NEWLINE
            simple_stmts : simple_stmt_list NEWLINE
          
            simple_stmt_list : simple_stmt
            simple_stmt_list : simple_stmt_list ';' simple_stmt
          
            // ── Simple statements ─────────────────────────────────────────────────
          
            simple_stmt : assignment
            simple_stmt : type_alias
            simple_stmt : star_expressions
            simple_stmt : return_stmt
            simple_stmt : import_stmt
            simple_stmt : raise_stmt
            simple_stmt : pass_stmt
            simple_stmt : del_stmt
            simple_stmt : yield_stmt
            simple_stmt : assert_stmt
            simple_stmt : break_stmt
            simple_stmt : continue_stmt
            simple_stmt : global_stmt
            simple_stmt : nonlocal_stmt
          
            // ── Compound statements ───────────────────────────────────────────────
          
            compound_stmt : function_def
            compound_stmt : if_stmt
            compound_stmt : class_def
            compound_stmt : with_stmt
            compound_stmt : for_stmt
            compound_stmt : try_stmt
            compound_stmt : while_stmt
            compound_stmt : match_stmt
          
            // ── Assignment ────────────────────────────────────────────────────────
          
            assignment : NAME ':' expression
            assignment : NAME ':' expression '=' annotated_rhs
            assignment : '(' single_target ')' ':' expression
            assignment : '(' single_target ')' ':' expression '=' annotated_rhs
            assignment : single_subscript_attribute_target ':' expression
            assignment : single_subscript_attribute_target ':' expression '=' annotated_rhs
            assignment : star_targets_eq_list annotated_rhs
            assignment : single_target augassign annotated_rhs
          
            // One or more (star_targets '=')
            star_targets_eq_list : star_targets '='
            star_targets_eq_list : star_targets_eq_list star_targets '='
          
            annotated_rhs : yield_expr
            annotated_rhs : star_expressions
          
            augassign : '+='
            augassign : '-='
            augassign : '*='
            augassign : '@='
            augassign : '/='
            augassign : '%='
            augassign : '&='
            augassign : '|='
            augassign : '^='
            augassign : '<<='
            augassign : '>>='
            augassign : '**='
            augassign : '//='
          
            // ── Simple statement bodies ───────────────────────────────────────────
          
            return_stmt : 'return'
            return_stmt : 'return' star_expressions
          
            raise_stmt : 'raise'
            raise_stmt : 'raise' expression
            raise_stmt : 'raise' expression 'from' expression
          
            pass_stmt : 'pass'
            break_stmt : 'break'
            continue_stmt : 'continue'
          
            global_stmt : 'global' name_list
            nonlocal_stmt : 'nonlocal' name_list
          
            // comma-separated NAME list (one or more)
            name_list : NAME
            name_list : name_list ',' NAME
          
            del_stmt : 'del' del_targets
          
            yield_stmt : yield_expr
          
            assert_stmt : 'assert' expression
            assert_stmt : 'assert' expression ',' expression
          
            // ── Import ────────────────────────────────────────────────────────────
          
            import_stmt : import_name
            import_stmt : import_from
          
            import_name : 'import' dotted_as_names
          
            import_from : 'from' dotted_name 'import' import_from_targets
            import_from : 'from' dots 'import' import_from_targets
            import_from : 'from' dots dotted_name 'import' import_from_targets
          
            // One or more dots (. or ...)
            dots : dot_or_ellipsis
            dots : dots dot_or_ellipsis
          
            dot_or_ellipsis : '.'
            dot_or_ellipsis : '...'
          
            import_from_targets : '(' import_from_as_names ')'
            import_from_targets : '(' import_from_as_names ',' ')'
            import_from_targets : import_from_as_names
            import_from_targets : '*'
          
            import_from_as_names : import_from_as_name
            import_from_as_names : import_from_as_names ',' import_from_as_name
          
            import_from_as_name : NAME
            import_from_as_name : NAME 'as' NAME
          
            dotted_as_names : dotted_as_name
            dotted_as_names : dotted_as_names ',' dotted_as_name
          
            dotted_as_name : dotted_name
            dotted_as_name : dotted_name 'as' NAME
          
            dotted_name : NAME
            dotted_name : dotted_name '.' NAME
          
            // ── Block ─────────────────────────────────────────────────────────────
          
            block : NEWLINE INDENT statements DEDENT
            block : simple_stmts
          
            // ── Decorators ────────────────────────────────────────────────────────
          
            decorators : decorator
            decorators : decorators decorator
          
            decorator : '@' named_expression NEWLINE
          
            // ── Class definition ──────────────────────────────────────────────────
          
            class_def : decorators class_def_raw
            class_def : class_def_raw
          
            class_def_raw : 'class' NAME ':' block
            class_def_raw : 'class' NAME type_params ':' block
            class_def_raw : 'class' NAME '(' ')' ':' block
            class_def_raw : 'class' NAME '(' arguments ')' ':' block
            class_def_raw : 'class' NAME type_params '(' ')' ':' block
            class_def_raw : 'class' NAME type_params '(' arguments ')' ':' block
          
            // ── Function definition ───────────────────────────────────────────────
          
            function_def : decorators function_def_raw
            function_def : function_def_raw
          
            function_def_raw : 'def' NAME '(' ')' ':' block
            function_def_raw : 'def' NAME '(' ')' ':' func_type_comment block
            function_def_raw : 'def' NAME '(' ')' '->' expression ':' block
            function_def_raw : 'def' NAME '(' ')' '->' expression ':' func_type_comment block
            function_def_raw : 'def' NAME '(' params ')' ':' block
            function_def_raw : 'def' NAME '(' params ')' ':' func_type_comment block
            function_def_raw : 'def' NAME '(' params ')' '->' expression ':' block
            function_def_raw : 'def' NAME '(' params ')' '->' expression ':' func_type_comment block
            function_def_raw : 'def' NAME type_params '(' ')' ':' block
            function_def_raw : 'def' NAME type_params '(' ')' ':' func_type_comment block
            function_def_raw : 'def' NAME type_params '(' ')' '->' expression ':' block
            function_def_raw : 'def' NAME type_params '(' ')' '->' expression ':' func_type_comment block
            function_def_raw : 'def' NAME type_params '(' params ')' ':' block
            function_def_raw : 'def' NAME type_params '(' params ')' ':' func_type_comment block
            function_def_raw : 'def' NAME type_params '(' params ')' '->' expression ':' block
            function_def_raw : 'def' NAME type_params '(' params ')' '->' expression ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME '(' ')' ':' block
            function_def_raw : 'async' 'def' NAME '(' ')' ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME '(' ')' '->' expression ':' block
            function_def_raw : 'async' 'def' NAME '(' ')' '->' expression ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME '(' params ')' ':' block
            function_def_raw : 'async' 'def' NAME '(' params ')' ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME '(' params ')' '->' expression ':' block
            function_def_raw : 'async' 'def' NAME '(' params ')' '->' expression ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME type_params '(' ')' ':' block
            function_def_raw : 'async' 'def' NAME type_params '(' ')' ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME type_params '(' ')' '->' expression ':' block
            function_def_raw : 'async' 'def' NAME type_params '(' ')' '->' expression ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME type_params '(' params ')' ':' block
            function_def_raw : 'async' 'def' NAME type_params '(' params ')' ':' func_type_comment block
            function_def_raw : 'async' 'def' NAME type_params '(' params ')' '->' expression ':' block
            function_def_raw : 'async' 'def' NAME type_params '(' params ')' '->' expression ':' func_type_comment block
          
            // ── Parameters ────────────────────────────────────────────────────────
          
            params : parameters
          
            parameters : slash_no_default star_etc
            parameters : slash_no_default
            parameters : slash_no_default param_no_default_list star_etc
            parameters : slash_no_default param_no_default_list
            parameters : slash_no_default param_with_default_list star_etc
            parameters : slash_no_default param_with_default_list
            parameters : slash_no_default param_no_default_list param_with_default_list star_etc
            parameters : slash_no_default param_no_default_list param_with_default_list
            parameters : slash_with_default star_etc
            parameters : slash_with_default
            parameters : slash_with_default param_with_default_list star_etc
            parameters : slash_with_default param_with_default_list
            parameters : param_no_default_list star_etc
            parameters : param_no_default_list
            parameters : param_no_default_list param_with_default_list star_etc
            parameters : param_no_default_list param_with_default_list
            parameters : param_with_default_list star_etc
            parameters : param_with_default_list
            parameters : star_etc
          
            param_no_default_list : param_no_default
            param_no_default_list : param_no_default_list param_no_default
          
            param_with_default_list : param_with_default
            param_with_default_list : param_with_default_list param_with_default
          
            param_maybe_default_list : param_maybe_default
            param_maybe_default_list : param_maybe_default_list param_maybe_default
          
            slash_no_default : param_no_default_list '/' ','
            slash_no_default : param_no_default_list '/'
          
            slash_with_default : param_with_default_list '/' ','
            slash_with_default : param_with_default_list '/'
            slash_with_default : param_no_default_list param_with_default_list '/' ','
            slash_with_default : param_no_default_list param_with_default_list '/'
          
            star_etc : '*' param_no_default kwds
            star_etc : '*' param_no_default
            star_etc : '*' param_no_default param_maybe_default_list kwds
            star_etc : '*' param_no_default param_maybe_default_list
            star_etc : '*' param_no_default_star_annotation kwds
            star_etc : '*' param_no_default_star_annotation
            star_etc : '*' param_no_default_star_annotation param_maybe_default_list kwds
            star_etc : '*' param_no_default_star_annotation param_maybe_default_list
            star_etc : '*' ',' param_maybe_default_list kwds
            star_etc : '*' ',' param_maybe_default_list
            star_etc : kwds
          
            kwds : '**' param_no_default
          
            param_no_default : param ','
            param_no_default : param
            param_no_default : param TYPE_COMMENT ','
            param_no_default : param TYPE_COMMENT
          
            param_no_default_star_annotation : param_star_annotation ','
            param_no_default_star_annotation : param_star_annotation
            param_no_default_star_annotation : param_star_annotation TYPE_COMMENT ','
            param_no_default_star_annotation : param_star_annotation TYPE_COMMENT
          
            param_with_default : param default ','
            param_with_default : param default
            param_with_default : param default TYPE_COMMENT ','
            param_with_default : param default TYPE_COMMENT
          
            param_maybe_default : param ','
            param_maybe_default : param
            param_maybe_default : param default ','
            param_maybe_default : param default
            param_maybe_default : param TYPE_COMMENT ','
            param_maybe_default : param TYPE_COMMENT
            param_maybe_default : param default TYPE_COMMENT ','
            param_maybe_default : param default TYPE_COMMENT
          
            param : NAME
            param : NAME annotation
          
            param_star_annotation : NAME star_annotation
          
            annotation : ':' expression
            star_annotation : ':' star_expression
          
            default : '=' expression
          
            // ── If / elif / else ──────────────────────────────────────────────────
          
            if_stmt : 'if' named_expression ':' block elif_stmt
            if_stmt : 'if' named_expression ':' block
            if_stmt : 'if' named_expression ':' block else_block
          
            elif_stmt : 'elif' named_expression ':' block elif_stmt
            elif_stmt : 'elif' named_expression ':' block
            elif_stmt : 'elif' named_expression ':' block else_block
          
            else_block : 'else' ':' block
          
            // ── While ─────────────────────────────────────────────────────────────
          
            while_stmt : 'while' named_expression ':' block
            while_stmt : 'while' named_expression ':' block else_block
          
            // ── For ───────────────────────────────────────────────────────────────
          
            for_stmt : 'for' star_targets 'in' star_expressions ':' block
            for_stmt : 'for' star_targets 'in' star_expressions ':' TYPE_COMMENT block
            for_stmt : 'for' star_targets 'in' star_expressions ':' block else_block
            for_stmt : 'for' star_targets 'in' star_expressions ':' TYPE_COMMENT block else_block
            for_stmt : 'async' 'for' star_targets 'in' star_expressions ':' block
            for_stmt : 'async' 'for' star_targets 'in' star_expressions ':' TYPE_COMMENT block
            for_stmt : 'async' 'for' star_targets 'in' star_expressions ':' block else_block
            for_stmt : 'async' 'for' star_targets 'in' star_expressions ':' TYPE_COMMENT block else_block
          
            // ── With ──────────────────────────────────────────────────────────────
          
            with_stmt : 'with' '(' with_item_list ')' ':' block
            with_stmt : 'with' '(' with_item_list ',' ')' ':' block
            with_stmt : 'with' '(' with_item_list ')' ':' TYPE_COMMENT block
            with_stmt : 'with' with_item_list ':' block
            with_stmt : 'with' with_item_list ':' TYPE_COMMENT block
            with_stmt : 'async' 'with' '(' with_item_list ')' ':' block
            with_stmt : 'async' 'with' '(' with_item_list ',' ')' ':' block
            with_stmt : 'async' 'with' with_item_list ':' block
            with_stmt : 'async' 'with' with_item_list ':' TYPE_COMMENT block
          
            with_item_list : with_item
            with_item_list : with_item_list ',' with_item
          
            with_item : expression 'as' star_target
            with_item : expression
          
            // ── Try / except / finally ────────────────────────────────────────────
          
            try_stmt : 'try' ':' block finally_block
            try_stmt : 'try' ':' block except_block_list
            try_stmt : 'try' ':' block except_block_list else_block
            try_stmt : 'try' ':' block except_block_list finally_block
            try_stmt : 'try' ':' block except_block_list else_block finally_block
            try_stmt : 'try' ':' block except_star_block_list
            try_stmt : 'try' ':' block except_star_block_list else_block
            try_stmt : 'try' ':' block except_star_block_list finally_block
            try_stmt : 'try' ':' block except_star_block_list else_block finally_block
          
            except_block_list : except_block
            except_block_list : except_block_list except_block
          
            except_star_block_list : except_star_block
            except_star_block_list : except_star_block_list except_star_block
          
            except_block : 'except' ':' block
            except_block : 'except' expression ':' block
            except_block : 'except' expression 'as' NAME ':' block
            except_block : 'except' expressions ':' block
          
            except_star_block : 'except' '*' expression ':' block
            except_star_block : 'except' '*' expression 'as' NAME ':' block
            except_star_block : 'except' '*' expressions ':' block
          
            finally_block : 'finally' ':' block
          
            // ── Match ─────────────────────────────────────────────────────────────
          
            match_stmt : NAME subject_expr ':' NEWLINE INDENT case_block_list DEDENT
          
            subject_expr : star_named_expression ',' star_named_expressions
            subject_expr : star_named_expression ','
            subject_expr : named_expression
          
            case_block_list : case_block
            case_block_list : case_block_list case_block
          
            case_block : 'case' patterns ':' block
            case_block : 'case' patterns guard ':' block
          
            guard : 'if' named_expression
          
            patterns : open_sequence_pattern
            patterns : pattern
          
            pattern : as_pattern
            pattern : or_pattern
          
            as_pattern : or_pattern 'as' pattern_capture_target
          
            or_pattern : closed_pattern
            or_pattern : or_pattern '|' closed_pattern
          
            closed_pattern : literal_pattern
            closed_pattern : capture_pattern
            closed_pattern : wildcard_pattern
            closed_pattern : value_pattern
            closed_pattern : group_pattern
            closed_pattern : sequence_pattern
            closed_pattern : mapping_pattern
            closed_pattern : class_pattern
          
            literal_pattern : signed_number
            literal_pattern : complex_number
            literal_pattern : strings
            literal_pattern : 'None'
            literal_pattern : 'True'
            literal_pattern : 'False'
          
            literal_expr : signed_number
            literal_expr : complex_number
            literal_expr : strings
            literal_expr : 'None'
            literal_expr : 'True'
            literal_expr : 'False'
          
            complex_number : signed_real_number '+' imaginary_number
            complex_number : signed_real_number '-' imaginary_number
          
            signed_number : NUMBER
            signed_number : '-' NUMBER
          
            signed_real_number : real_number
            signed_real_number : '-' real_number
          
            real_number : NUMBER
          
            imaginary_number : NUMBER
          
            capture_pattern : pattern_capture_target
          
            pattern_capture_target : NAME
          
            wildcard_pattern : NAME
          
            value_pattern : attr
          
            attr : name_or_attr '.' NAME
          
            name_or_attr : attr
            name_or_attr : NAME
          
            group_pattern : '(' pattern ')'
          
            sequence_pattern : '[' ']'
            sequence_pattern : '[' maybe_sequence_pattern ']'
            sequence_pattern : '(' ')'
            sequence_pattern : '(' open_sequence_pattern ')'
          
            open_sequence_pattern : maybe_star_pattern ','
            open_sequence_pattern : maybe_star_pattern ',' maybe_sequence_pattern
          
            maybe_sequence_pattern : maybe_star_pattern
            maybe_sequence_pattern : maybe_sequence_pattern ',' maybe_star_pattern
            maybe_sequence_pattern : maybe_sequence_pattern ','
          
            maybe_star_pattern : star_pattern
            maybe_star_pattern : pattern
          
            star_pattern : '*' pattern_capture_target
            star_pattern : '*' wildcard_pattern
          
            mapping_pattern : '{' '}'
            mapping_pattern : '{' double_star_pattern '}'
            mapping_pattern : '{' double_star_pattern ',' '}'
            mapping_pattern : '{' items_pattern '}'
            mapping_pattern : '{' items_pattern ',' '}'
            mapping_pattern : '{' items_pattern ',' double_star_pattern '}'
            mapping_pattern : '{' items_pattern ',' double_star_pattern ',' '}'
          
            items_pattern : key_value_pattern
            items_pattern : items_pattern ',' key_value_pattern
          
            key_value_pattern : literal_expr ':' pattern
            key_value_pattern : attr ':' pattern
          
            double_star_pattern : '**' pattern_capture_target
          
            class_pattern : name_or_attr '(' ')'
            class_pattern : name_or_attr '(' positional_patterns ')'
            class_pattern : name_or_attr '(' positional_patterns ',' ')'
            class_pattern : name_or_attr '(' keyword_patterns ')'
            class_pattern : name_or_attr '(' keyword_patterns ',' ')'
            class_pattern : name_or_attr '(' positional_patterns ',' keyword_patterns ')'
            class_pattern : name_or_attr '(' positional_patterns ',' keyword_patterns ',' ')'
          
            positional_patterns : pattern
            positional_patterns : positional_patterns ',' pattern
          
            keyword_patterns : keyword_pattern
            keyword_patterns : keyword_patterns ',' keyword_pattern
          
            keyword_pattern : NAME '=' pattern
          
            // ── Type alias / type params ──────────────────────────────────────────
          
            type_alias : NAME NAME '=' expression
            type_alias : NAME NAME type_params '=' expression
          
            type_params : '[' type_param_seq ']'
          
            type_param_seq : type_param
            type_param_seq : type_param_seq ',' type_param
            type_param_seq : type_param_seq ',' type_param ','
          
            type_param : NAME
            type_param : NAME type_param_bound
            type_param : NAME type_param_bound type_param_default
            type_param : NAME type_param_default
            type_param : '*' NAME
            type_param : '*' NAME type_param_starred_default
            type_param : '**' NAME
            type_param : '**' NAME type_param_default
          
            type_param_bound : ':' expression
            type_param_default : '=' expression
            type_param_starred_default : '=' star_expression
          
            // ── Expressions ───────────────────────────────────────────────────────
          
            expressions : expression
            expressions : expression ','
            expressions : expression ',' expression_list
            expressions : expression ',' expression_list ','
          
            expression_list : expression
            expression_list : expression_list ',' expression
          
            expression : disjunction
            expression : lambdef
            expression : disjunction 'if' disjunction 'else' expression
          
            yield_expr : 'yield'
            yield_expr : 'yield' star_expressions
            yield_expr : 'yield' 'from' expression
          
            star_expressions : star_expression
            star_expressions : star_expression ','
            star_expressions : star_expression ',' star_expression_list
            star_expressions : star_expression ',' star_expression_list ','
          
            star_expression_list : star_expression
            star_expression_list : star_expression_list ',' star_expression
          
            star_expression : '*' bitwise_or
            star_expression : expression
          
            star_named_expressions : star_named_expression
            star_named_expressions : star_named_expressions ',' star_named_expression
            star_named_expressions : star_named_expressions ','
          
            star_named_expression : '*' bitwise_or
            star_named_expression : named_expression
          
            assignment_expression : NAME ':=' expression
          
            named_expression : assignment_expression
            named_expression : expression
          
            disjunction : conjunction
            disjunction : disjunction 'or' conjunction
          
            conjunction : inversion
            conjunction : conjunction 'and' inversion
          
            inversion : 'not' inversion
            inversion : comparison
          
            // ── Comparison ────────────────────────────────────────────────────────
          
            comparison : bitwise_or
            comparison : bitwise_or compare_op_pair_list
          
            compare_op_pair_list : compare_op_bitwise_or_pair
            compare_op_pair_list : compare_op_pair_list compare_op_bitwise_or_pair
          
            compare_op_bitwise_or_pair : '==' bitwise_or
            compare_op_bitwise_or_pair : '!=' bitwise_or
            compare_op_bitwise_or_pair : '<=' bitwise_or
            compare_op_bitwise_or_pair : '<' bitwise_or
            compare_op_bitwise_or_pair : '>=' bitwise_or
            compare_op_bitwise_or_pair : '>' bitwise_or
            compare_op_bitwise_or_pair : 'not' 'in' bitwise_or
            compare_op_bitwise_or_pair : 'in' bitwise_or
            compare_op_bitwise_or_pair : 'is' 'not' bitwise_or
            compare_op_bitwise_or_pair : 'is' bitwise_or
          
            // ── Bitwise operators ─────────────────────────────────────────────────
          
            bitwise_or : bitwise_xor
            bitwise_or : bitwise_or '|' bitwise_xor
          
            bitwise_xor : bitwise_and
            bitwise_xor : bitwise_xor '^' bitwise_and
          
            bitwise_and : shift_expr
            bitwise_and : bitwise_and '&' shift_expr
          
            shift_expr : sum
            shift_expr : shift_expr '<<' sum
            shift_expr : shift_expr '>>' sum
          
            // ── Arithmetic ────────────────────────────────────────────────────────
          
            sum : term
            sum : sum '+' term
            sum : sum '-' term
          
            term : factor
            term : term '*' factor
            term : term '/' factor
            term : term '//' factor
            term : term '%' factor
            term : term '@' factor
          
            factor : '+' factor
            factor : '-' factor
            factor : '~' factor
            factor : power
          
            power : await_primary
            power : await_primary '**' factor
          
            // ── Primary ───────────────────────────────────────────────────────────
          
            await_primary : 'await' primary
            await_primary : primary
          
            primary : atom
            primary : primary '.' NAME
            primary : primary genexp
            primary : primary '(' ')'
            primary : primary '(' arguments ')'
            primary : primary '[' slices ']'
          
            slices : slice
            slices : slice_or_starred_list
            slices : slice_or_starred_list ','
          
            slice_or_starred_list : slice_or_starred
            slice_or_starred_list : slice_or_starred_list ',' slice_or_starred
          
            slice_or_starred : slice
            slice_or_starred : starred_expression
          
            slice : named_expression
            slice : ':'
            slice : ':' expression
            slice : expression ':'
            slice : expression ':' expression
            slice : ':' ':' expression
            slice : ':' expression ':'
            slice : ':' expression ':' expression
            slice : expression ':' ':'
            slice : expression ':' ':' expression
            slice : expression ':' expression ':'
            slice : expression ':' expression ':' expression
          
            atom : NAME
            atom : 'True'
            atom : 'False'
            atom : 'None'
            atom : strings
            atom : NUMBER
            atom : tuple
            atom : group
            atom : genexp
            atom : list
            atom : listcomp
            atom : dict
            atom : set
            atom : dictcomp
            atom : setcomp
            atom : '...'
          
            group : '(' yield_expr ')'
            group : '(' named_expression ')'
          
            // ── Lambda ────────────────────────────────────────────────────────────
          
            lambdef : 'lambda' ':' expression
            lambdef : 'lambda' lambda_params ':' expression
          
            lambda_params : lambda_parameters
          
            lambda_parameters : lambda_slash_no_default lambda_star_etc
            lambda_parameters : lambda_slash_no_default
            lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_star_etc
            lambda_parameters : lambda_slash_no_default lambda_param_no_default_list
            lambda_parameters : lambda_slash_no_default lambda_param_with_default_list lambda_star_etc
            lambda_parameters : lambda_slash_no_default lambda_param_with_default_list
            lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_param_with_default_list lambda_star_etc
            lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_param_with_default_list
            lambda_parameters : lambda_slash_with_default lambda_star_etc
            lambda_parameters : lambda_slash_with_default
            lambda_parameters : lambda_slash_with_default lambda_param_with_default_list lambda_star_etc
            lambda_parameters : lambda_slash_with_default lambda_param_with_default_list
            lambda_parameters : lambda_param_no_default_list lambda_star_etc
            lambda_parameters : lambda_param_no_default_list
            lambda_parameters : lambda_param_no_default_list lambda_param_with_default_list lambda_star_etc
            lambda_parameters : lambda_param_no_default_list lambda_param_with_default_list
            lambda_parameters : lambda_param_with_default_list lambda_star_etc
            lambda_parameters : lambda_param_with_default_list
            lambda_parameters : lambda_star_etc
          
            lambda_param_no_default_list : lambda_param_no_default
            lambda_param_no_default_list : lambda_param_no_default_list lambda_param_no_default
          
            lambda_param_with_default_list : lambda_param_with_default
            lambda_param_with_default_list : lambda_param_with_default_list lambda_param_with_default
          
            lambda_param_maybe_default_list : lambda_param_maybe_default
            lambda_param_maybe_default_list : lambda_param_maybe_default_list lambda_param_maybe_default
          
            lambda_slash_no_default : lambda_param_no_default_list '/' ','
            lambda_slash_no_default : lambda_param_no_default_list '/'
          
            lambda_slash_with_default : lambda_param_with_default_list '/' ','
            lambda_slash_with_default : lambda_param_with_default_list '/'
            lambda_slash_with_default : lambda_param_no_default_list lambda_param_with_default_list '/' ','
            lambda_slash_with_default : lambda_param_no_default_list lambda_param_with_default_list '/'
          
            lambda_star_etc : '*' lambda_param_no_default lambda_kwds
            lambda_star_etc : '*' lambda_param_no_default
            lambda_star_etc : '*' lambda_param_no_default lambda_param_maybe_default_list lambda_kwds
            lambda_star_etc : '*' lambda_param_no_default lambda_param_maybe_default_list
            lambda_star_etc : '*' ',' lambda_param_maybe_default_list lambda_kwds
            lambda_star_etc : '*' ',' lambda_param_maybe_default_list
            lambda_star_etc : lambda_kwds
          
            lambda_kwds : '**' lambda_param_no_default
          
            lambda_param_no_default : lambda_param ','
            lambda_param_no_default : lambda_param
          
            lambda_param_with_default : lambda_param default ','
            lambda_param_with_default : lambda_param default
          
            lambda_param_maybe_default : lambda_param ','
            lambda_param_maybe_default : lambda_param
            lambda_param_maybe_default : lambda_param default ','
            lambda_param_maybe_default : lambda_param default
          
            lambda_param : NAME
          
            // ── f-strings / t-strings ─────────────────────────────────────────────
          
            // TODO !
          
            // ── String / list / tuple / set / dict / comprehensions ───────────────
          
            string : STRING
          
            strings : string
            strings : strings string
          
            list : '[' ']'
            list : '[' star_named_expressions ']'
          
            tuple : '(' ')'
            tuple : '(' star_named_expression ',' ')'
            tuple : '(' star_named_expression ',' star_named_expressions ')'
            tuple : '(' star_named_expression ',' star_named_expressions ',' ')'
          
            set : '{' star_named_expressions '}'
          
            dict : '{' '}'
            dict : '{' double_starred_kvpairs '}'
          
            double_starred_kvpairs : double_starred_kvpair
            double_starred_kvpairs : double_starred_kvpairs ',' double_starred_kvpair
            double_starred_kvpairs : double_starred_kvpairs ','
          
            double_starred_kvpair : '**' bitwise_or
            double_starred_kvpair : kvpair
          
            kvpair : expression ':' expression
          
            // ── Comprehensions / generators ───────────────────────────────────────
          
            for_if_clauses : for_if_clause
            for_if_clauses : for_if_clauses for_if_clause
          
            for_if_clause : 'for' star_targets 'in' disjunction
            for_if_clause : 'for' star_targets 'in' disjunction if_clause_list
            for_if_clause : 'async' 'for' star_targets 'in' disjunction
            for_if_clause : 'async' 'for' star_targets 'in' disjunction if_clause_list
          
            if_clause_list : 'if' disjunction
            if_clause_list : if_clause_list 'if' disjunction
          
            listcomp : '[' named_expression for_if_clauses ']'
          
            setcomp : '{' named_expression for_if_clauses '}'
          
            genexp : '(' assignment_expression for_if_clauses ')'
            genexp : '(' expression for_if_clauses ')'
          
            dictcomp : '{' kvpair for_if_clauses '}'
          
            // ── Function call arguments ───────────────────────────────────────────
          
            arguments : args
            arguments : args ','
          
            args : args_items
            args : args_items ',' kwargs
          
            args_items : starred_expression
            args_items : assignment_expression
            args_items : expression
            args_items : args_items ',' starred_expression
            args_items : args_items ',' assignment_expression
            args_items : args_items ',' expression
          
            kwargs : kwarg_or_starred_list ',' kwarg_or_double_starred_list
            kwargs : kwarg_or_starred_list
            kwargs : kwarg_or_double_starred_list
          
            kwarg_or_starred_list : kwarg_or_starred
            kwarg_or_starred_list : kwarg_or_starred_list ',' kwarg_or_starred
          
            kwarg_or_double_starred_list : kwarg_or_double_starred
            kwarg_or_double_starred_list : kwarg_or_double_starred_list ',' kwarg_or_double_starred
          
            starred_expression : '*' expression
          
            kwarg_or_starred : NAME '=' expression
            kwarg_or_starred : starred_expression
          
            kwarg_or_double_starred : NAME '=' expression
            kwarg_or_double_starred : '**' expression
          
            // ── Assignment targets ────────────────────────────────────────────────
          
            star_targets : star_target
            star_targets : star_target ','
            star_targets : star_target ',' star_target_list
            star_targets : star_target ',' star_target_list ','
          
            star_target_list : star_target
            star_target_list : star_target_list ',' star_target
          
            star_targets_list_seq : star_target
            star_targets_list_seq : star_targets_list_seq ',' star_target
            star_targets_list_seq : star_targets_list_seq ','
          
            star_targets_tuple_seq : star_target ',' star_target
            star_targets_tuple_seq : star_target ','
            star_targets_tuple_seq : star_target ',' star_target_list
            star_targets_tuple_seq : star_target ',' star_target_list ','
          
            star_target : '*' star_target
            star_target : target_with_star_atom
          
            target_with_star_atom : t_primary '.' NAME
            target_with_star_atom : t_primary '[' slices ']'
            target_with_star_atom : star_atom
          
            star_atom : NAME
            star_atom : '(' target_with_star_atom ')'
            star_atom : '(' ')'
            star_atom : '(' star_targets_tuple_seq ')'
            star_atom : '[' ']'
            star_atom : '[' star_targets_list_seq ']'
          
            single_target : single_subscript_attribute_target
            single_target : NAME
            single_target : '(' single_target ')'
          
            single_subscript_attribute_target : t_primary '.' NAME
            single_subscript_attribute_target : t_primary '[' slices ']'
          
            t_primary : atom
            t_primary : t_primary '.' NAME
            t_primary : t_primary '[' slices ']'
            t_primary : t_primary genexp
            t_primary : t_primary '(' ')'
            t_primary : t_primary '(' arguments ')'
          
            // ── Del targets ───────────────────────────────────────────────────────
          
            del_targets : del_target
            del_targets : del_targets ',' del_target
            del_targets : del_targets ','
          
            del_target : t_primary '.' NAME
            del_target : t_primary '[' slices ']'
            del_target : del_t_atom
          
            del_t_atom : NAME
            del_t_atom : '(' del_target ')'
            del_t_atom : '(' ')'
            del_t_atom : '(' del_targets ')'
            del_t_atom : '[' ']'
            del_t_atom : '[' del_targets ']'
          
            // ── Typing elements ───────────────────────────────────────────────────
          
            type_expressions : expression_comma_list ',' '*' expression ',' '**' expression
            type_expressions : expression_comma_list ',' '*' expression
            type_expressions : expression_comma_list ',' '**' expression
            type_expressions : '*' expression ',' '**' expression
            type_expressions : '*' expression
            type_expressions : '**' expression
            type_expressions : expression_comma_list
          
            expression_comma_list : expression
            expression_comma_list : expression_comma_list ',' expression
          
            func_type_comment : NEWLINE TYPE_COMMENT
            func_type_comment : TYPE_COMMENT
          
          }
          """);

  {
    //META_GRAMMAR.verify();
  }

  private static void parse(String source) {
    META_GRAMMAR.parse(source, new Evaluator<@Nullable Object>() {
      @Override
      public @Nullable Object evaluate(Terminal terminal) {
        return null;
      }

      @Override
      public @Nullable Object evaluate(Production production, List<@Nullable Object> arguments) {
        return null;
      }
    });
  }

  @Test
  public void pass() {
    parse("""
      pass;
      """);
  }

  @Nested
  public class LiteralsAndExpressions {
    @Test
    void testNumericLiterals() {
      parse("42");
      parse("3.14159");
      parse("1e-9");
      parse("0b1010");
      parse("0x7f");
      parse("3 + 4j"); // Complex numbers
    }

    @Test
    void testStringLiterals() {
      parse("'single quotes'");
      parse("\"double quotes\"");
      parse("r'raw string\\n'");
      parse("f'f-string {variable}'");
      parse("\"\"\"triple double quotes\"\"\"");
      parse("'''triple single\nmulti-line'''");
    }

    @Test
    void testCollections() {
      parse("[1, 2, 3, 4]"); // List
      parse("(1, 2, 3)");    // Tuple
      parse("{1, 2, 3}");    // Set
      parse("{'a': 1, 'b': 2}"); // Dict
      parse("{}");           // Empty dict
    }
  }

  @Nested
  public class OperatorsAndPrecedence {
    @Test
    void testArithmetic() {
      parse("x = a + b * c ** d / e // f % g");
      parse("x = -a + ~b");
    }

    @Test
    void testBitwiseAndBoolean() {
      parse("x = (a & b) | (c ^ d) >> 2 << 1");
      parse("x = not a and b or c");
    }

    @Test
    void testComparisons() {
      parse("x = a == b != c < d <= e > f >= g");
      parse("x = a is b and c is not d");
      parse("x = e in f or g not in h");
    }

    @Test
    void testTernaryAndWalrus() {
      parse("x = true_val if condition else false_val");
      parse("if (x := call()) > 0: pass");
    }
  }

  @Nested
  public class ControlFlow {
    @Test
    void testIfStatements() {
      parse("""
        if x > 0:
            print("positive")
        elif x < 0:
            print("negative")
        else:
            print("zero")
        """);
    }

    @Test
    void testWhileLoops() {
      parse("""
        while condition:
            break
        else:
            continue
        """);
    }

    @Test
    void testForLoops() {
      parse("""
        for i in range(10):
            if i == 5:
                continue
            print(i)
        """);
    }

    @Test
    void testMatchCase() { // Python 3.10+
      parse("""
        match status:
            case 200:
                return "OK"
            case 404 | 405:
                return "Not Found"
            case _:
                return "Unknown"
        """);
    }
  }

  @Nested
  public class FunctionsAndLambdas {
    @Test
    void testBasicFunction() {
      parse("""
        def greet(name):
            return f"Hello, {name}"
        """);
    }

    @Test
    void testComplexArguments() {
      parse("""
        def complex_func(a, b=10, *args, kw_only, **kwargs):
            yield a
            return
        """);
    }

    @Test
    void testTypeHinting() {
      parse("""
        def add(x: int, y: int = 0) -> int:
            return x + y
        """);
    }

    @Test
    void testLambdasAndAsync() {
      parse("f = lambda x, y=1: x + y");
      parse("""
        async def fetch():
            await asyncio.sleep(1)
        """);
    }
  }

  @Nested
  public class Classes {
    @Test
    void testBasicClass() {
      parse("""
        class Empty:
            pass
        """);
    }

    @Test
    void testInheritanceAndMethods() {
      parse("""
        @decorator
        class Dog(Animal, Pack):
            def __init__(self, name: str):
                super().__init__()
                self._name = name
                
            def bark(self):
                return "woof"
        """);
    }
  }

  @Nested
  public class ExceptionsAndContexts {
    @Test
    void testTryExceptFinally() {
      parse("""
        try:
            raise ValueError("error")
        except TypeError as e:
            pass
        except (AttributeError, KeyError):
            log_error()
        else:
            print("success")
        finally:
            cleanup()
        """);
    }

    @Test
    void testWithStatements() {
      parse("""
        with open("file.txt") as f, open("out.txt", "w") as out:
            out.write(f.read())
        """);
    }
  }

  @Nested
  public class ComprehensionsAndSlicing {
    @Test
    void testComprehensions() {
      parse("[x**2 for x in items if x > 0]");
      parse("{k: v for k, v in dict.items()}");
      parse("(x for x in generator)");
    }

    @Test
    void testSlicingAndSubscripts() {
      parse("matrix[0][1]");
      parse("array[1:10:2]");
      parse("array[:5]");
      parse("array[5:]");
      parse("multi_dim[1:3, ::-1]");
    }
  }

  @Nested
  public class ImportsAndModules {
    @Test
    void testImports() {
      parse("import os, sys");
      parse("import numpy as np");
      parse("from math import pi, sqrt as s");
      parse("from .relative import sibling");
      parse("from ...parent import grandparent");
    }

    @Test
    void testGlobalNonlocal() {
      parse("""
        def outer():
            x = 1
            def inner():
                nonlocal x
                global y
                x = 2
        """);
    }
  }
}
