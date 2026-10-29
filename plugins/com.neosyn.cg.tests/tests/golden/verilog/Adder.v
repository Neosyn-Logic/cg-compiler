/**
 * Title      : Generated from com.neosyn.test.golden.Adder by Neosyn IDE
 * File       : com.neosyn.test.golden.Adder.v
 * Standard   : Verilog-2001
 */

/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *
 * Golden file test - simple task for regression testing.
 * This file is used to verify that IR and HDL generation produce consistent output.
 */

/**
 * Simple adder task for golden file testing.
 * Adds two inputs and produces the sum.
 */
module Adder(
  input clock,
  input reset_n,
  input [7 : 0] a,
  input [7 : 0] b,
  output reg [7 : 0] sum
);


  /**
   * State variables
   */
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Adder
    if (~reset_n) begin
      sum <= 8'b0;
    end else begin
      
      // line 33
      sum <= (a + b);
    end
  end

endmodule //Adder
