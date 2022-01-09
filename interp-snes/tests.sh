#!/bin/bash

rm -rf results
mkdir results

pass=0
total=0

do_test() {
	exp="expected/"
	res="results/"
	exp="$exp$1.bin"
	exp_txt="expected/$1.txt"
	res="$res$1.bin"
	res_txt="results/$1.txt"
	test="tests/$1.txt"
	
	if [ $1 = "26_operations" ];
	then	
		java -cp bin assembler.Interp $test > $res_txt
		 
		if cmp -s $exp_txt $res_txt
		then
			echo "TEST $1: PASSED"
		else
			echo "TEST $1: FAILED"
		fi
	else
		java -cp bin assembler.Interp $test
	 
		if cmp -s $exp $res
		then
			echo "TEST $1: PASSED"
		else
			echo "TEST $1: FAILED"
		fi
	fi
}

do_test "1_byte_operand"
do_test "2_word_operand"
do_test "3_word_operand"
do_test "4_long_operand"
do_test "5_decimal"
do_test "6_decimal"
do_test "7_binary"
do_test "8_binary"
do_test "9_binary"
do_test "10_label"
do_test "11_label"
do_test "12_label"
do_test "13_label"
do_test "14_label"
do_test "15_label"
do_test "16_expression"
do_test "17_label"
do_test "18_macro"
do_test "19_macro"
do_test "20_macro"
do_test "21_db_inst"
do_test "22_db_inst"
do_test "23_dw_inst"
do_test "24_dl_inst"
do_test "25_acc_flag"
do_test "26_operations"
do_test "27_fill_inst"
do_test "28_namespace"
do_test "29_namespace"
do_test "30_namespace"
do_test "31_while"
do_test "32_while_if"
do_test "33_prog_a"
do_test "34_anon_labels"
do_test "35_cmp"
do_test "36_lda"
do_test "37_sta"
