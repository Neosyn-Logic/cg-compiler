/**
 * Title      : Generated from com.neosyn.test.golden.Producer by Neosyn IDE
 * File       : com.neosyn.test.golden.Producer.v
 * Standard   : Verilog-2001
 */

/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *
 * Golden file test - Network compositions for regression testing.
 * Tests: task instantiation, port connections, inline tasks.
 */

/**
 * Simple producer task - generates incrementing values.
 */
module Producer(
  input clock,
  input reset_n,
  output reg [7 : 0] data
);


  /**
   * State variables
   */
  reg [7 : 0] value;
  
  
  
  
  
  /**
   * Synchronous process
   */
  always @(negedge reset_n or posedge clock) begin // body of Producer
    if (~reset_n) begin
      value <= 8'h0;
      data <= 8'b0;
    end else begin
      
      // line 18
      data <= value;
      value <= (value + 8'h1);
    end
  end

endmodule //Producer
