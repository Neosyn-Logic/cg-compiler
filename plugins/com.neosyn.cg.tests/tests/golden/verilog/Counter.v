/**
 * Title      : Generated from com.neosyn.test.golden.Counter by Neosyn IDE
 * File       : com.neosyn.test.golden.Counter.v
 * Standard   : Verilog-2001
 */

/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *
 * Golden file test - simple task for regression testing.
 * This file is used to verify that IR and HDL generation produce consistent output.
 */

/**
 * Simple counter task for golden file testing.
 * Increments a counter on each cycle and outputs it.
 */
module Counter(
  input clock,
  input reset_n,
  output reg [7 : 0] count
);


  /**
   * State variables
   */
  reg [7 : 0] counter;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Counter
    if (~reset_n) begin
      counter <= 8'h0;
      count <= 8'b0;
    end else begin
      
      // line 19
      count <= counter;
      counter <= (counter + 8'h1);
    end
  end

endmodule //Counter
