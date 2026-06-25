/*
 * Copyright (c) 2024 Neosyn. All rights reserved.
 *  *
 * Golden file test - Network compositions for regression testing.
 * Tests: task instantiation, port connections, inline tasks.
 */

/**
 * Simple producer task - generates incrementing values.
 */
module Producer(input clock, input reset_n, output reg [7 : 0] data);


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
      
      begin : FSM_Producer_a // line 18
        reg [8 : 0] orion;
      
        data <= value;
        orion = (value + 8'h1);
        value <= orion[7 : 0];
      end
    end
  end

endmodule //Producer
